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
                .dockerImage(
                        normalizeNullableText(request.dockerImage())
                )
                .artifactPath(
                        normalizeNullableText(request.artifactPath())
                )
                .buildLogsUrl(
                        normalizeNullableText(request.buildLogsUrl())
                )
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

        return versionRepository.findAllByDeletedFalse()
                .stream()
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

        return versionRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() ->
                        new VersionNotFoundException(id)
                );
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
                version.getUpdatedAt()
        );
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

        Specification<Version> specification =
                VersionSpecifications.notDeleted()
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