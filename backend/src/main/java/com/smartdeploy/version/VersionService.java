package com.smartdeploy.version;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.ApplicationNotFoundException;
import com.smartdeploy.application.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
@Service
@Transactional
public class VersionService {

    /* ==========================================================
     *                      DEPENDENCIES
     * ==========================================================
     */

    private final VersionRepository versionRepository;
    private final ApplicationRepository applicationRepository;

    /* ==========================================================
     *                      CONSTRUCTOR
     * ==========================================================
     */

    private VersionUsage usage;

    /** Facultatif : usage des versions (en service, déploiements en cours, verdict). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setUsage(VersionUsage usage) {
        this.usage = usage;
    }

    public VersionService(
            VersionRepository versionRepository,
            ApplicationRepository applicationRepository
    ) {
        this.versionRepository = versionRepository;
        this.applicationRepository = applicationRepository;
    }

    /* ==========================================================
     *                      CREATE VERSION
     * ==========================================================
     */

    private com.smartdeploy.application.ApplicationAccess access;

    /** RG-O1 : contrôle d'accès aux applications (facultatif, absent des tests unitaires). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAccess(com.smartdeploy.application.ApplicationAccess access) {
        this.access = access;
    }

    public VersionResponse create(
            CreateVersionRequest request
    ) {
        Application application = applicationRepository
                .findByIdAndDeletedFalse(request.applicationId())
                .orElseThrow(() ->
                        new ApplicationNotFoundException(
                                request.applicationId()
                        )
                );
        if (access != null) {
            access.ensureCanAccess(application);
        }

        String normalizedVersion =
                request.version().trim();

        boolean alreadyExists =
                versionRepository
                        .existsByApplicationAndVersionAndDeletedFalse(
                                application,
                                normalizedVersion
                        );

        if (alreadyExists) {
            throw new VersionAlreadyExistsException(
                    application.getName(),
                    normalizedVersion
            );
        }

        Version version = Version.builder()
                .application(application)
                .version(normalizedVersion)
                .gitBranch(request.gitBranch().trim())
                .gitCommit(
                        normalizeNullableText(request.gitCommit())
                )
                .dockerImage(null)
                .artifactPath(null)
                .buildLogsUrl(null)
                .buildStatus(BuildStatus.PENDING)
                .deleted(false)
                .build();

        Version savedVersion =
                versionRepository.saveAndFlush(version);

        return toResponse(savedVersion);
    }

    /* ==========================================================
     *                      GET VERSION BY ID
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public VersionResponse findById(Long id) {

        Version version = findActiveVersionById(id);

        return toResponse(version);
    }

    /* ==========================================================
     *                      GET ALL VERSIONS
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public List<VersionResponse> findAll() {

        Long ownerId = access == null ? null : access.ownerFilter();
        return versionRepository.findAllByDeletedFalse()
                .stream()
                .filter(v -> ownerId == null || (v.getApplication().getOwner() != null
                        && ownerId.equals(v.getApplication().getOwner().getId())))
                .map(this::toResponse)
                .toList();
    }

    /* ==========================================================
     *              GET VERSIONS BY APPLICATION
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public List<VersionResponse> findByApplication(
            Long applicationId
    ) {
        Application application = applicationRepository
                .findByIdAndDeletedFalse(applicationId)
                .orElseThrow(() ->
                        new ApplicationNotFoundException(
                                applicationId
                        )
                );
        if (access != null) {
            access.ensureCanAccess(application);
        }

        return versionRepository
                .findAllByApplicationAndDeletedFalse(application)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /* ==========================================================
     *                      UPDATE VERSION
     * ==========================================================
     */

    public VersionResponse update(
            Long id,
            UpdateVersionRequest request
    ) {
        Version version = findActiveVersionById(id);

        String normalizedVersion =
                request.version().trim();

        // RG-V3 : une version figée (déjà construite) ne change plus de numéro, de branche ni de commit.
        if (version.getPinnedAt() != null) {
            boolean identityChanged = !version.getVersion().equals(normalizedVersion)
                    || !version.getGitBranch().equals(request.gitBranch().trim())
                    || !java.util.Objects.equals(version.getGitCommit(), normalizeNullableText(request.gitCommit()));
            if (identityChanged) {
                throw new VersionStateException(
                        "Version figée depuis sa première construction"
                                + (version.getGitCommit() == null ? "" : " (commit " + shortCommit(version.getGitCommit()) + ")")
                                + " : son numéro, sa branche et son commit ne peuvent plus changer. Créez une nouvelle version."
                );
            }
        }

        boolean versionChanged =
                !version.getVersion()
                        .equalsIgnoreCase(normalizedVersion);

        if (versionChanged) {
            boolean alreadyExists =
                    versionRepository
                            .existsByApplicationAndVersionAndDeletedFalse(
                                    version.getApplication(),
                                    normalizedVersion
                            );

            if (alreadyExists) {
                throw new VersionAlreadyExistsException(
                        version.getApplication().getName(),
                        normalizedVersion
                );
            }
        }

        version.setVersion(normalizedVersion);
        version.setGitBranch(request.gitBranch().trim());
        version.setGitCommit(
                normalizeNullableText(request.gitCommit())
        );
        version.setDockerImage(
                normalizeNullableText(request.dockerImage())
        );
        version.setArtifactPath(
                normalizeNullableText(request.artifactPath())
        );
        version.setBuildLogsUrl(
                normalizeNullableText(request.buildLogsUrl())
        );

        Version updatedVersion =
                versionRepository.saveAndFlush(version);

        return toResponse(updatedVersion);
    }

    /* ==========================================================
     *                  UPDATE BUILD STATUS
     * ==========================================================
     */

    public VersionResponse updateBuildStatus(
            Long id,
            BuildStatus buildStatus
    ) {
        Version version = findActiveVersionById(id);

        if (version.getBuildStatus() == buildStatus) {
            throw new VersionStateException(
                    "La version possède déjà le statut "
                            + buildStatus
            );
        }

        version.setBuildStatus(buildStatus);

        Version updatedVersion =
                versionRepository.saveAndFlush(version);

        return toResponse(updatedVersion);
    }

    /* ==========================================================
     *                  UPDATE BUILD METADATA
     * ==========================================================
     */

    public VersionResponse updateBuildMetadata(
            Long id,
            Integer buildNumber,
            String dockerImage,
            String artifactPath,
            String buildLogsUrl
    ) {
        Version version = findActiveVersionById(id);

        version.setBuildNumber(buildNumber);
        version.setDockerImage(
                normalizeNullableText(dockerImage)
        );
        version.setArtifactPath(
                normalizeNullableText(artifactPath)
        );
        version.setBuildLogsUrl(
                normalizeNullableText(buildLogsUrl)
        );

        Version updatedVersion =
                versionRepository.saveAndFlush(version);

        return toResponse(updatedVersion);
    }

    /* ==========================================================
     *                      SOFT DELETE
     * ==========================================================
     */

    public void softDelete(Long id) {

        Version version = findActiveVersionById(id);

        if (version.getBuildStatus() == BuildStatus.RUNNING) {
            throw new VersionStateException(
                    "Impossible de supprimer une version dont le build est en cours"
            );
        }

        // RG-V9 : une version en service, ou en cours de déploiement, ne peut pas être supprimée.
        if (usage != null) {
            List<String> inService = usage.inServiceEnvironments(version.getId());
            if (!inService.isEmpty()) {
                throw new VersionStateException(
                        "La version " + version.getVersion() + " est en service sur : " + String.join(", ", inService)
                                + ". Déployez d'abord une autre version."
                );
            }
            if (usage.hasActiveDeployment(version.getId())) {
                throw new VersionStateException(
                        "Un déploiement de la version " + version.getVersion() + " est en attente ou en cours."
                );
            }
        }

        version.setDeleted(true);

        versionRepository.saveAndFlush(version);
    }

    /* ==========================================================
     *                      RESTORE VERSION
     * ==========================================================
     */

    public VersionResponse restore(Long id) {

        Version version = versionRepository.findById(id)
                .orElseThrow(() ->
                        new VersionNotFoundException(id)
                );

        if (!version.isDeleted()) {
            throw new VersionStateException(
                    "Cette version n'est pas supprimée"
            );
        }

        boolean alreadyExists =
                versionRepository
                        .existsByApplicationAndVersionAndDeletedFalse(
                                version.getApplication(),
                                version.getVersion()
                        );

        if (alreadyExists) {
            throw new VersionAlreadyExistsException(
                    version.getApplication().getName(),
                    version.getVersion()
            );
        }

        version.setDeleted(false);

        Version restoredVersion =
                versionRepository.saveAndFlush(version);

        return toResponse(restoredVersion);
    }

    /* ==========================================================
     *                      PRIVATE HELPERS
     * ==========================================================
     */

    private Version findActiveVersionById(Long id) {

        Version version = versionRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() ->
                        new VersionNotFoundException(id)
                );
        if (access != null) {
            access.ensureCanAccess(version.getApplication());
        }
        return version;
    }

    private String normalizeNullableText(String value) {

        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();

        return normalizedValue.isEmpty()
                ? null
                : normalizedValue;
    }

    private VersionResponse toResponse(
            Version version
    ) {
        VersionUsage.Verdict verdict = usage == null || version.getId() == null ? null : usage.lastVerdict(version.getId());

        return new VersionResponse(
                version.getId(),
                version.getApplication().getId(),
                version.getApplication().getName(),
                version.getVersion(),
                version.getGitBranch(),
                version.getGitCommit(),
                version.getDockerImage(),
                version.getBuildStatus(),
                version.getBuildNumber(),
                version.getArtifactPath(),
                version.getBuildLogsUrl(),
                version.isDeleted(),
                version.getCreatedAt(),
                version.getUpdatedAt(),
                version.getPinnedAt(),
                version.getPinnedAt() != null,
                usage == null || version.getId() == null ? List.of() : usage.inServiceEnvironments(version.getId()),
                verdict == null ? null : verdict.label(),
                verdict == null ? null : verdict.tone(),
                usage == null || version.getId() == null ? List.of() : usage.validatedEnvironments(version.getId())
        );
    }

    private static String shortCommit(String commit) {
        return commit.length() > 7 ? commit.substring(0, 7) : commit;
    }
    /* ==========================================================
     *              SEARCH AND PAGINATE VERSIONS
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public VersionPageResponse searchVersions(
            int page,
            int size,
            String search,
            BuildStatus buildStatus,
            Long applicationId
    ) {
        int safePage = Math.max(page, 0);

        int safeSize = Math.min(
                Math.max(size, 1),
                100
        );

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(
                        Sort.Order.desc("createdAt")
                )
        );

        Long ownerId = access == null ? null : access.ownerFilter();
        Specification<Version> specification =
                VersionSpecifications.ownedBy(ownerId).and(VersionSpecifications.notDeleted())
                        .and(
                                VersionSpecifications
                                        .containsSearch(search)
                        )
                        .and(
                                VersionSpecifications
                                        .hasBuildStatus(buildStatus)
                        )
                        .and(
                                VersionSpecifications
                                        .hasApplicationId(applicationId)
                        );

        Page<Version> versionPage =
                versionRepository.findAll(
                        specification,
                        pageable
                );

        return new VersionPageResponse(
                versionPage.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                versionPage.getNumber(),
                versionPage.getSize(),
                versionPage.getTotalElements(),
                versionPage.getTotalPages(),
                versionPage.isFirst(),
                versionPage.isLast()
        );
    }
}