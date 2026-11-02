package com.smartdeploy.deployment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentNotFoundException;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.user.User;
import com.smartdeploy.version.BuildStatus;
import com.smartdeploy.version.Version;
import com.smartdeploy.version.VersionNotFoundException;
import com.smartdeploy.version.VersionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import com.smartdeploy.integration.kubernetes.KubernetesNamespaceService;
import com.smartdeploy.integration.kubernetes.KubernetesApplicationRequest;
import com.smartdeploy.integration.kubernetes.KubernetesDatabaseRequest;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

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

    private final ApplicationEventPublisher eventPublisher;
    private final DeploymentKubernetesConfigRepository kubernetesConfigRepository;
    private final KubernetesNamespaceService kubernetesNamespaceService;
    private final KubernetesClient kubernetesClient;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /* ==========================================================
     *                      CONSTRUCTOR
     * ==========================================================
     */

    public DeploymentService(
            DeploymentRepository deploymentRepository,
            VersionRepository versionRepository,
            EnvironmentRepository environmentRepository,
            ApplicationEventPublisher eventPublisher,
            DeploymentKubernetesConfigRepository kubernetesConfigRepository,
            KubernetesNamespaceService kubernetesNamespaceService,
            KubernetesClient kubernetesClient
    ) {
        this.deploymentRepository = deploymentRepository;
        this.versionRepository = versionRepository;
        this.environmentRepository = environmentRepository;
        this.eventPublisher = eventPublisher;
        this.kubernetesConfigRepository = kubernetesConfigRepository;
        this.kubernetesNamespaceService = kubernetesNamespaceService;
        this.kubernetesClient = kubernetesClient;
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

        // Conserver la configuration Kubernetes demandée pour ce déploiement.
        if (request.kubernetesConfig() == null) {
            throw new DeploymentValidationException(
                    "La configuration Kubernetes est obligatoire"
            );
        }

        boolean hasFrontend = request.kubernetesConfig().frontend() != null;
        boolean hasBackend = request.kubernetesConfig().backend() != null;
        if (!hasFrontend && !hasBackend) {
            throw new DeploymentValidationException(
                    "Au moins un composant frontend ou backend est obligatoire"
            );
        }


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

        // Le namespace est la source de vérité de l'environnement,
        // jamais une valeur libre provenant du navigateur.
        String namespace = environment.getNamespace();
        if (namespace == null || !namespace.matches(
                "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$"
        ) || namespace.length() > 63) {
            throw new DeploymentValidationException(
                    "Le namespace de l'environnement est absent ou invalide"
            );
        }

        // Créer le namespace s'il n'existe pas (avant le déploiement).
        kubernetesNamespaceService.ensureExists(namespace);

        final String configurationJson;
        try {
            ObjectNode normalizedConfig = OBJECT_MAPPER.valueToTree(
                    request.kubernetesConfig()
            );
            normalizedConfig.put("namespace", namespace);
            configurationJson = OBJECT_MAPPER.writeValueAsString(normalizedConfig);
        } catch (JsonProcessingException exception) {
            throw new DeploymentValidationException(
                    "Impossible de sérialiser la configuration Kubernetes"
            );
        }

        DeploymentKubernetesConfig kubernetesConfig =
                new DeploymentKubernetesConfig();
        kubernetesConfig.setDeployment(savedDeployment);
        kubernetesConfig.setNamespace(namespace);
        kubernetesConfig.setConfigurationJson(configurationJson);

        kubernetesConfig.setApplicationType(
                hasFrontend && hasBackend ? KubernetesApplicationType.FULLSTACK
                        : hasFrontend ? KubernetesApplicationType.FRONTEND
                        : KubernetesApplicationType.BACKEND
        );

        kubernetesConfigRepository.saveAndFlush(kubernetesConfig);

        // Valider que le Secret Kubernetes existe si une base de données est configurée.
        validateDatabaseSecret(namespace, request.kubernetesConfig());

        // Jenkins est déclenché APRÈS le commit (DeploymentPipelineLauncher) :
        // le déploiement et sa configuration sont alors visibles pour le
        // webhook et l'export des manifests. Un échec Jenkins devient FAILED.
        eventPublisher.publishEvent(
                new DeploymentPipelineRequestedEvent(savedDeployment.getId())
        );

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

        deployment.setKubernetesDeploymentName(
                normalizeNullableText(
                        request.kubernetesDeploymentName()
                )
        );

        Deployment savedDeployment =
                deploymentRepository.saveAndFlush(
                        deployment
                );

        return toResponse(savedDeployment);
    }

    /* ==========================================================
     *              JENKINS PIPELINE CALLBACK (WEBHOOK)
     * ==========================================================
     *
     * Contrairement à updateStatus (action manuelle, transitions
     * strictes), ce traitement tolère la réalité d'un réseau :
     *  - callback répété            → DUPLICATE, sans erreur ;
     *  - callback tardif/hors ordre → IGNORED si le déploiement
     *                                 est déjà terminé ;
     *  - RUNNING perdu              → PENDING peut passer
     *                                 directement à l'état final.
     * Métadonnées et statut sont appliqués dans une seule transaction.
     */
    public PipelineCallbackResult applyPipelineCallback(
            Long deploymentId,
            DeploymentStatus newStatus,
            Integer buildNumber,
            String logsUrl,
            String errorMessage,
            String kubernetesDeploymentName
    ) {
        return applyPipelineCallback(
                deploymentId, newStatus, buildNumber, logsUrl,
                errorMessage, kubernetesDeploymentName, null, null
        );
    }

    public PipelineCallbackResult applyPipelineCallback(
            Long deploymentId,
            DeploymentStatus newStatus,
            Integer buildNumber,
            String logsUrl,
            String errorMessage,
            String kubernetesDeploymentName,
            String backendImage,
            String frontendImage
    ) {
        if (deploymentId == null) {
            throw new DeploymentValidationException(
                    "deploymentId est obligatoire"
            );
        }

        if (newStatus == null
                || newStatus == DeploymentStatus.PENDING
                || newStatus == DeploymentStatus.ROLLED_BACK) {
            throw new DeploymentValidationException(
                    "Statut de callback Jenkins non autorisé : " + newStatus
            );
        }

        Deployment deployment = deploymentRepository
                .findActiveByIdForUpdate(deploymentId)
                .orElseThrow(() ->
                        new DeploymentNotFoundException(deploymentId)
                );

        // Un callback d'un autre build Jenkins ne doit pas modifier
        // ce déploiement (relance manuelle du job, rejeu, etc.).
        if (buildNumber != null
                && deployment.getJenkinsBuildNumber() != null
                && !buildNumber.equals(deployment.getJenkinsBuildNumber())) {
            throw new DeploymentStateException(
                    "Le déploiement " + deploymentId
                            + " est associé au build Jenkins #"
                            + deployment.getJenkinsBuildNumber()
                            + ", callback reçu du build #" + buildNumber
            );
        }

        DeploymentStatus currentStatus = deployment.getStatus();

        // Build démarré malgré une annulation : la réponse 409 fait échouer
        // l'étape « Notify RUNNING » du Jenkinsfile, avant tout déploiement.
        if (currentStatus == DeploymentStatus.CANCELLED
                && newStatus == DeploymentStatus.RUNNING) {
            throw new DeploymentStateException(
                    "Le déploiement " + deploymentId
                            + " a été annulé dans SmartDeploy : le pipeline doit s'arrêter"
            );
        }

        if (isTerminal(currentStatus) && currentStatus != newStatus) {
            return new PipelineCallbackResult(
                    deploymentId,
                    currentStatus,
                    PipelineCallbackOutcome.IGNORED
            );
        }

        applyPipelineMetadata(
                deployment,
                buildNumber,
                logsUrl,
                kubernetesDeploymentName,
                backendImage,
                frontendImage
        );

        if (currentStatus == newStatus) {
            deploymentRepository.saveAndFlush(deployment);
            return new PipelineCallbackResult(
                    deploymentId,
                    currentStatus,
                    PipelineCallbackOutcome.DUPLICATE
            );
        }

        LocalDateTime now = LocalDateTime.now();

        switch (newStatus) {
            case RUNNING -> {
                deployment.setStartedAt(now);
                deployment.setFinishedAt(null);
                deployment.setDurationSeconds(null);
                deployment.setErrorMessage(null);
            }
            case SUCCESS -> completeDeployment(deployment, now, null);
            case FAILED -> {
                String normalizedError = normalizeNullableText(errorMessage);
                completeDeployment(
                        deployment,
                        now,
                        normalizedError == null
                                ? "Échec signalé par Jenkins sans détail"
                                : normalizedError
                );
            }
            case CANCELLED -> completeDeployment(
                    deployment,
                    now,
                    normalizeNullableText(errorMessage)
            );
            default -> throw new IllegalStateException(
                    "Statut inattendu : " + newStatus
            );
        }

        deployment.setStatus(newStatus);
        deploymentRepository.saveAndFlush(deployment);

        if (newStatus == DeploymentStatus.SUCCESS) {
            markVersionBuilt(deployment);
        }

        return new PipelineCallbackResult(
                deploymentId,
                newStatus,
                PipelineCallbackOutcome.APPLIED
        );
    }

    /*
     * Cycle de vie d'une version :
     *  - PENDING : jamais construite et déployée avec succès ;
     *  - SUCCESS : le pipeline Jenkins a construit son image, l'a publiée
     *              et l'a déployée avec succès au moins une fois.
     *
     * Un échec de déploiement ne rend PAS la version FAILED : Jenkins ne
     * précise pas l'étape en échec (build, qualité, rollout Kubernetes…),
     * et une version FAILED ne serait plus déployable. Elle reste donc
     * dans son état actuel et peut être redéployée.
     */
    private void markVersionBuilt(Deployment deployment) {
        Version version = deployment.getVersion();

        if (version == null || version.isDeleted()) {
            return;
        }

        version.setBuildStatus(BuildStatus.SUCCESS);

        // La version garde la référence exacte de l'image construite.
        if (deployment.getDeployedBackendImage() != null) {
            version.setDockerImage(deployment.getDeployedBackendImage());
        }

        if (deployment.getJenkinsBuildNumber() != null) {
            version.setBuildNumber(deployment.getJenkinsBuildNumber());
        }

        if (deployment.getDeploymentLogsUrl() != null) {
            version.setBuildLogsUrl(deployment.getDeploymentLogsUrl());
        }

        versionRepository.saveAndFlush(version);
    }

    private void applyPipelineMetadata(
            Deployment deployment,
            Integer buildNumber,
            String logsUrl,
            String kubernetesDeploymentName,
            String backendImage,
            String frontendImage
    ) {
        String normalizedBackendImage = normalizeImageReference(backendImage);
        if (normalizedBackendImage != null) {
            deployment.setDeployedBackendImage(normalizedBackendImage);
        }

        String normalizedFrontendImage = normalizeImageReference(frontendImage);
        if (normalizedFrontendImage != null) {
            deployment.setDeployedFrontendImage(normalizedFrontendImage);
        }

        // Une valeur absente n'efface jamais une valeur déjà connue.
        if (buildNumber != null) {
            deployment.setJenkinsBuildNumber(buildNumber);
        }

        String normalizedLogsUrl = normalizeNullableText(logsUrl);
        if (normalizedLogsUrl != null) {
            deployment.setDeploymentLogsUrl(normalizedLogsUrl);
        }

        String normalizedName = normalizeNullableText(kubernetesDeploymentName);
        if (normalizedName != null) {
            deployment.setKubernetesDeploymentName(normalizedName);
        }
    }

    private static final java.util.regex.Pattern IMAGE_REFERENCE =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._:/@-]{0,499}$");

    /** Référence d'image acceptée telle quelle, ou null si absente / invalide. */
    static String normalizeImageReference(String image) {
        if (image == null || image.isBlank()) {
            return null;
        }
        String trimmed = image.trim();
        return IMAGE_REFERENCE.matcher(trimmed).matches() ? trimmed : null;
    }

    private static boolean isTerminal(DeploymentStatus status) {
        return status == DeploymentStatus.SUCCESS
                || status == DeploymentStatus.FAILED
                || status == DeploymentStatus.CANCELLED
                || status == DeploymentStatus.ROLLED_BACK;
    }

    /* ==========================================================
     *                  CANCEL (ACTION UTILISATEUR)
     * ==========================================================
     *
     * Appelé par DeploymentPipelineLauncher APRÈS la demande d'arrêt
     * envoyée à Jenkins (hors transaction). Le statut est revérifié
     * sous verrou : le pipeline a pu se terminer entre-temps.
     */
    public DeploymentResponse markCancelled(
            Long deploymentId,
            User cancelledBy,
            String jenkinsMessage
    ) {
        Deployment deployment = deploymentRepository
                .findActiveByIdForUpdate(deploymentId)
                .orElseThrow(() ->
                        new DeploymentNotFoundException(deploymentId)
                );

        ensureCancellable(deployment);

        String author = cancelledBy != null && cancelledBy.getEmail() != null
                ? cancelledBy.getEmail()
                : "un utilisateur";
        String reason = "Annulé par " + author + "."
                + (jenkinsMessage == null || jenkinsMessage.isBlank() ? "" : " " + jenkinsMessage.trim());

        completeDeployment(
                deployment,
                LocalDateTime.now(),
                reason.length() <= 2000 ? reason : reason.substring(0, 2000)
        );
        deployment.setStatus(DeploymentStatus.CANCELLED);

        return toResponse(deploymentRepository.saveAndFlush(deployment));
    }

    void ensureCancellable(Deployment deployment) {
        DeploymentStatus status = deployment.getStatus();
        if (status != DeploymentStatus.PENDING && status != DeploymentStatus.RUNNING) {
            throw new DeploymentStateException(
                    "Seul un déploiement en attente ou en cours peut être annulé (statut actuel : "
                            + status + ")"
            );
        }
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

        if (version.getBuildStatus() == null
                || (version.getBuildStatus() != BuildStatus.PENDING
                && version.getBuildStatus() != BuildStatus.SUCCESS)) {

            throw new DeploymentValidationException(
                    "La version doit être en attente de build "
                            + "ou avoir un build réussi pour être déployée"
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
                deployment.getUpdatedAt(),
                deployment.getDeployedBackendImage(),
                deployment.getDeployedFrontendImage()
        );
    }

    private void validateDatabaseSecret(
            String namespace,
            KubernetesApplicationRequest kubernetesConfig
    ) {
        if (kubernetesConfig == null || kubernetesConfig.database() == null) {
            return;
        }

        KubernetesDatabaseRequest database = kubernetesConfig.database();

        if (database.passwordSecretName() == null ||
                database.passwordSecretName().isBlank()) {
            throw new DeploymentValidationException(
                    "Le nom du Secret Kubernetes pour la base de données est obligatoire"
            );
        }

        try {
            var secret = kubernetesClient.secrets()
                    .inNamespace(namespace)
                    .withName(database.passwordSecretName())
                    .get();

            if (secret == null) {
                throw new DeploymentValidationException(
                        "Le Secret Kubernetes '" + database.passwordSecretName() +
                                "' n'existe pas dans le namespace '" + namespace + "'"
                );
            }

            if (database.passwordSecretKey() != null &&
                    !database.passwordSecretKey().isBlank()) {
                var secretData = secret.getData();
                if (secretData == null || !secretData.containsKey(database.passwordSecretKey())) {
                    throw new DeploymentValidationException(
                            "La clé '" + database.passwordSecretKey() +
                                    "' n'existe pas dans le Secret Kubernetes '" +
                                    database.passwordSecretName() + "'"
                    );
                }
            }
        } catch (KubernetesClientException e) {
            if (e.getCode() == 403 || e.getCode() == 401) {
                throw new DeploymentValidationException(
                        "Permissions Kubernetes insuffisantes pour vérifier le Secret de base de données"
                );
            }
            throw new DeploymentValidationException(
                    "Impossible de vérifier le Secret Kubernetes '" +
                            database.passwordSecretName() + "' : erreur HTTP " + e.getCode()
            );
        }
    }
}
