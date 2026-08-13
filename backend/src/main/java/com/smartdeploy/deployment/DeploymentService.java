package com.smartdeploy.deployment;

import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentNotFoundException;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.user.User;
import com.smartdeploy.version.BuildStatus;
import com.smartdeploy.version.Version;
import com.smartdeploy.version.VersionNotFoundException;
import com.smartdeploy.version.VersionRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import com.smartdeploy.integration.jenkins.JenkinsIntegrationException;
import com.smartdeploy.integration.jenkins.JenkinsService;

@Service
@Transactional
public class DeploymentService {

    /* ==========================================================
     *                      DEPENDENCIES
     * ==========================================================
     */

    private final DeploymentRepository deploymentRepository;
    private final VersionRepository versionRepository;
    private final EnvironmentRepository environmentRepository;

    private final JenkinsService jenkinsService;
    /* ==========================================================
     *                      CONSTRUCTOR
     * ==========================================================
     */

    public DeploymentService(
            DeploymentRepository deploymentRepository,
            VersionRepository versionRepository,
            EnvironmentRepository environmentRepository,
            JenkinsService jenkinsService
    ) {
        this.deploymentRepository = deploymentRepository;
        this.versionRepository = versionRepository;
        this.environmentRepository = environmentRepository;
        this.jenkinsService = jenkinsService;
    }

    /* ==========================================================
     *                  CREATE DEPLOYMENT
     * ==========================================================
     */

    public DeploymentResponse create(
            CreateDeploymentRequest request,
            User currentUser
    ) {
        Version version =
                versionRepository
                        .findByIdAndDeletedFalse(
                                request.versionId()
                        )
                        .orElseThrow(() ->
                                new VersionNotFoundException(
                                        request.versionId()
                                )
                        );

        Environment environment =
                environmentRepository
                        .findByIdAndDeletedFalse(
                                request.environmentId()
                        )
                        .orElseThrow(() ->
                                new EnvironmentNotFoundException(
                                        request.environmentId()
                                )
                        );

        validateDeployment(
                version,
                environment,
                currentUser
        );

        boolean deploymentAlreadyRunning =
                deploymentRepository
                        .existsByEnvironmentAndStatusInAndDeletedFalse(
                                environment,
                                List.of(
                                        DeploymentStatus.PENDING,
                                        DeploymentStatus.RUNNING
                                )
                        );

        if (deploymentAlreadyRunning) {
            throw new DeploymentStateException(
                    "Un déploiement est déjà en attente ou en cours "
                            + "sur cet environnement"
            );
        }

        Deployment deployment =
                Deployment.builder()
                        .version(version)
                        .environment(environment)
                        .requestedBy(currentUser)
                        .status(
                                DeploymentStatus.PENDING
                        )
                        .deleted(false)
                        .build();

        Deployment savedDeployment =
                deploymentRepository.saveAndFlush(
                        deployment
                );

        try {

            jenkinsService.triggerDeployment(
                    savedDeployment
            );

        } catch (
                JenkinsIntegrationException exception
        ) {

            savedDeployment.setStatus(
                    DeploymentStatus.FAILED
            );

            savedDeployment.setFinishedAt(
                    LocalDateTime.now()
            );

            savedDeployment.setErrorMessage(
                    exception.getMessage()
            );

            deploymentRepository.saveAndFlush(
                    savedDeployment
            );

            throw exception;
        }

        return toResponse(savedDeployment);
    }
    /* ==========================================================
     *                  GET DEPLOYMENT BY ID
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public DeploymentResponse findById(Long id) {

        return toResponse(
                findActiveDeploymentById(id)
        );
    }

    /* ==========================================================
     *                  UPDATE DEPLOYMENT STATUS
     * ==========================================================
     */

    public DeploymentResponse updateStatus(
            Long id,
            UpdateDeploymentStatusRequest request
    ) {
        Deployment deployment =
                findActiveDeploymentById(id);

        DeploymentStatus oldStatus =
                deployment.getStatus();

        DeploymentStatus newStatus =
                request.status();

        validateStatusTransition(
                oldStatus,
                newStatus
        );

        LocalDateTime now = LocalDateTime.now();

        if (newStatus == DeploymentStatus.RUNNING) {
            deployment.setStartedAt(now);
            deployment.setFinishedAt(null);
            deployment.setDurationSeconds(null);
            deployment.setErrorMessage(null);
        }

        if (newStatus == DeploymentStatus.SUCCESS) {
            completeDeployment(
                    deployment,
                    now,
                    null
            );
        }

        if (newStatus == DeploymentStatus.FAILED) {
            String normalizedError =
                    normalizeNullableText(
                            request.errorMessage()
                    );

            if (normalizedError == null) {
                throw new DeploymentValidationException(
                        "Un message d'erreur est obligatoire "
                                + "pour un déploiement échoué"
                );
            }

            completeDeployment(
                    deployment,
                    now,
                    normalizedError
            );
        }

        if (newStatus == DeploymentStatus.CANCELLED) {
            completeDeployment(
                    deployment,
                    now,
                    normalizeNullableText(
                            request.errorMessage()
                    )
            );
        }

        deployment.setStatus(newStatus);

        Deployment updatedDeployment =
                deploymentRepository.saveAndFlush(deployment);

        return toResponse(updatedDeployment);
    }

    /* ==========================================================
     *                  UPDATE JENKINS METADATA
     * ==========================================================
     */

    public DeploymentResponse updateMetadata(
            Long id,
            UpdateDeploymentMetadataRequest request
    ) {
        Deployment deployment =
                findActiveDeploymentById(id);

        deployment.setJenkinsBuildNumber(
                request.jenkinsBuildNumber()
        );

        deployment.setDeploymentLogsUrl(
                normalizeNullableText(
                        request.deploymentLogsUrl()
                )
        );

        Deployment updatedDeployment =
                deploymentRepository.saveAndFlush(deployment);

        return toResponse(updatedDeployment);
    }

    /* ==========================================================
     *                  CREATE ROLLBACK
     * ==========================================================
     */

    public DeploymentResponse createRollback(
            Long deploymentId,
            User currentUser
    ) {
        Deployment original =
                findActiveDeploymentById(deploymentId);

        if (original.getStatus()
                != DeploymentStatus.SUCCESS) {
            throw new DeploymentStateException(
                    "Seul un déploiement réussi peut servir "
                            + "de référence pour un rollback"
            );
        }

        boolean deploymentAlreadyRunning =
                deploymentRepository
                        .existsByEnvironmentAndStatusInAndDeletedFalse(
                                original.getEnvironment(),
                                List.of(
                                        DeploymentStatus.PENDING,
                                        DeploymentStatus.RUNNING
                                )
                        );

        if (deploymentAlreadyRunning) {
            throw new DeploymentStateException(
                    "Un déploiement est déjà en attente ou en cours "
                            + "sur cet environnement"
            );
        }

        Deployment rollback = Deployment.builder()
                .version(original.getVersion())
                .environment(original.getEnvironment())
                .requestedBy(currentUser)
                .status(DeploymentStatus.PENDING)
                .rollbackOf(original)
                .deleted(false)
                .build();

        Deployment savedRollback =
                deploymentRepository.saveAndFlush(rollback);

        return toResponse(savedRollback);
    }

    /* ==========================================================
     *                  SOFT DELETE DEPLOYMENT
     * ==========================================================
     */

    public void softDelete(Long id) {

        Deployment deployment =
                findActiveDeploymentById(id);

        if (deployment.getStatus()
                == DeploymentStatus.RUNNING) {
            throw new DeploymentStateException(
                    "Impossible de supprimer un déploiement en cours"
            );
        }

        deployment.setDeleted(true);

        deploymentRepository.saveAndFlush(deployment);
    }

    /* ==========================================================
     *              SEARCH AND PAGINATE DEPLOYMENTS
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public DeploymentPageResponse searchDeployments(
            int page,
            int size,
            String search,
            DeploymentStatus status,
            Long applicationId,
            Long versionId,
            Long environmentId
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

        Specification<Deployment> specification =
                DeploymentSpecifications.notDeleted()
                        .and(
                                DeploymentSpecifications
                                        .containsSearch(search)
                        )
                        .and(
                                DeploymentSpecifications
                                        .hasStatus(status)
                        )
                        .and(
                                DeploymentSpecifications
                                        .hasApplicationId(applicationId)
                        )
                        .and(
                                DeploymentSpecifications
                                        .hasVersionId(versionId)
                        )
                        .and(
                                DeploymentSpecifications
                                        .hasEnvironmentId(environmentId)
                        );

        Page<Deployment> deploymentPage =
                deploymentRepository.findAll(
                        specification,
                        pageable
                );

        return new DeploymentPageResponse(
                deploymentPage.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                deploymentPage.getNumber(),
                deploymentPage.getSize(),
                deploymentPage.getTotalElements(),
                deploymentPage.getTotalPages(),
                deploymentPage.isFirst(),
                deploymentPage.isLast()
        );
    }

    /* ==========================================================
     *                      PRIVATE HELPERS
     * ==========================================================
     */

    private Deployment findActiveDeploymentById(Long id) {

        return deploymentRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() ->
                        new DeploymentNotFoundException(id)
                );
    }

    private void validateDeployment(
            Version version,
            Environment environment,
            User currentUser
    ) {
        if (!version.getApplication().getId()
                .equals(
                        environment.getApplication().getId()
                )) {
            throw new DeploymentValidationException(
                    "La version et l'environnement "
                            + "n'appartiennent pas à la même application"
            );
        }

        if (!version.getApplication().isActive()
                || version.getApplication().isDeleted()) {
            throw new DeploymentValidationException(
                    "L'application est inactive ou supprimée"
            );
        }

        if (!environment.isActive()
                || environment.isDeleted()) {
            throw new DeploymentValidationException(
                    "L'environnement est inactif ou supprimé"
            );
        }

        if (version.isDeleted()) {
            throw new DeploymentValidationException(
                    "La version est supprimée"
            );
        }

        if (version.getBuildStatus()
                != BuildStatus.SUCCESS) {
            throw new DeploymentValidationException(
                    "Seule une version dont le build est réussi "
                            + "peut être déployée"
            );
        }

        if (currentUser == null
                || !currentUser.isEnabled()
                || currentUser.isDeleted()) {
            throw new DeploymentValidationException(
                    "L'utilisateur courant ne peut pas lancer "
                            + "de déploiement"
            );
        }
    }

    private void validateStatusTransition(
            DeploymentStatus oldStatus,
            DeploymentStatus newStatus
    ) {
        if (oldStatus == newStatus) {
            throw new DeploymentStateException(
                    "Le déploiement possède déjà le statut "
                            + newStatus
            );
        }

        boolean allowed = switch (oldStatus) {

            case PENDING ->
                    newStatus == DeploymentStatus.RUNNING
                            || newStatus == DeploymentStatus.CANCELLED;

            case RUNNING ->
                    newStatus == DeploymentStatus.SUCCESS
                            || newStatus == DeploymentStatus.FAILED
                            || newStatus == DeploymentStatus.CANCELLED;

            case SUCCESS ->
                    newStatus == DeploymentStatus.ROLLED_BACK;

            case FAILED, CANCELLED, ROLLED_BACK ->
                    false;
        };

        if (!allowed) {
            throw new DeploymentStateException(
                    "Transition de statut invalide : "
                            + oldStatus
                            + " → "
                            + newStatus
            );
        }
    }

    private void completeDeployment(
            Deployment deployment,
            LocalDateTime finishedAt,
            String errorMessage
    ) {
        deployment.setFinishedAt(finishedAt);
        deployment.setErrorMessage(errorMessage);

        if (deployment.getStartedAt() != null) {
            long duration = Duration.between(
                    deployment.getStartedAt(),
                    finishedAt
            ).getSeconds();

            deployment.setDurationSeconds(
                    Math.max(duration, 0)
            );
        }
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

    private DeploymentResponse toResponse(
            Deployment deployment
    ) {
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getVersion()
                        .getApplication()
                        .getId(),
                deployment.getVersion()
                        .getApplication()
                        .getName(),
                deployment.getVersion().getId(),
                deployment.getVersion().getVersion(),
                deployment.getEnvironment().getId(),
                deployment.getEnvironment().getName(),
                deployment.getEnvironment()
                        .getType()
                        .name(),
                deployment.getRequestedBy().getId(),
                deployment.getRequestedBy().getEmail(),
                deployment.getStatus(),
                deployment.getStartedAt(),
                deployment.getFinishedAt(),
                deployment.getDurationSeconds(),
                deployment.getJenkinsBuildNumber(),
                deployment.getDeploymentLogsUrl(),
                deployment.getErrorMessage(),
                deployment.getRollbackOf() == null
                        ? null
                        : deployment.getRollbackOf().getId(),
                deployment.isDeleted(),
                deployment.getCreatedAt(),
                deployment.getUpdatedAt()
        );
    }
}