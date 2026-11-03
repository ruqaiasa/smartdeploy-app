package com.smartdeploy.deployment;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.Technology;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.environment.EnvironmentType;
import com.smartdeploy.integration.kubernetes.KubernetesApplicationRequest;
import com.smartdeploy.integration.kubernetes.KubernetesComponentRequest;
import com.smartdeploy.integration.kubernetes.KubernetesNamespaceService;
import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import com.smartdeploy.version.BuildStatus;
import com.smartdeploy.version.Version;
import com.smartdeploy.version.VersionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.fabric8.kubernetes.client.KubernetesClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private VersionRepository versionRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private DeploymentKubernetesConfigRepository kubernetesConfigRepository;

    @Mock
    private KubernetesNamespaceService kubernetesNamespaceService;

    @Mock
    private KubernetesClient kubernetesClient;

    private DeploymentService deploymentService;

    @BeforeEach
    void setUp() {
        deploymentService = new DeploymentService(
                deploymentRepository,
                versionRepository,
                environmentRepository,
                eventPublisher,
                kubernetesConfigRepository,
                kubernetesNamespaceService,
                kubernetesClient
        );
    }

    /* ==========================================================
     *                  CREATE DEPLOYMENT
     * ==========================================================
     */

    @Test
    void create_shouldCreatePendingDeployment() {

        Application application = createApplication(
                100L,
                "SmartDeploy Backend"
        );

        Version version = createVersion(
                application,
                BuildStatus.SUCCESS
        );

        Environment environment =
                createEnvironment(application);

        User currentUser = createUser();

        KubernetesApplicationRequest kubernetesConfig =
                new KubernetesApplicationRequest(
                        "sd-dev-ecommerce",
                        null,
                        new KubernetesComponentRequest(
                                "ecommerce-backend",
                                "localhost:5000/ecommerce-backend:v1",
                                5,
                                8080
                        ),
                        null,
                        null
                );

        CreateDeploymentRequest request =
                new CreateDeploymentRequest(
                        1L,
                        2L,
                        kubernetesConfig
                );

        when(
                versionRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(
                Optional.of(version)
        );

        when(
                environmentRepository
                        .findByIdAndDeletedFalse(2L)
        ).thenReturn(
                Optional.of(environment)
        );

        when(
                deploymentRepository
                        .existsByEnvironmentAndStatusInAndDeletedFalse(
                                eq(environment),
                                any()
                        )
        ).thenReturn(false);

        when(
                deploymentRepository.saveAndFlush(
                        any(Deployment.class)
                )
        ).thenAnswer(invocation -> {

            Deployment deployment =
                    invocation.getArgument(0);

            deployment.setId(10L);
            deployment.setCreatedAt(
                    LocalDateTime.now()
            );
            deployment.setUpdatedAt(
                    LocalDateTime.now()
            );

            return deployment;
        });

        DeploymentResponse response =
                deploymentService.create(
                        request,
                        currentUser
                );

        assertNotNull(response);

        assertEquals(
                10L,
                response.id()
        );

        assertEquals(
                DeploymentStatus.PENDING,
                response.status()
        );

        assertEquals(
                1L,
                response.versionId()
        );

        assertEquals(
                "1.0.0",
                response.version()
        );

        assertEquals(
                2L,
                response.environmentId()
        );

        assertEquals(
                "Production",
                response.environmentName()
        );

        assertEquals(
                3L,
                response.requestedById()
        );

        assertEquals(
                "ruqaia@smartdeploy.com",
                response.requestedByEmail()
        );

        assertFalse(
                response.deleted()
        );

        verify(
                deploymentRepository
        ).saveAndFlush(
                any(Deployment.class)
        );

        verify(kubernetesConfigRepository).saveAndFlush(
                any(DeploymentKubernetesConfig.class)
        );

        // Jenkins n'est plus appelé dans la transaction : un événement
        // est publié et traité après commit par DeploymentPipelineLauncher.
        verify(eventPublisher).publishEvent(
                new DeploymentPipelineRequestedEvent(10L)
        );
    }

    @Test
    void create_shouldThrowWhenVersionAndEnvironmentHaveDifferentApplications() {

        Application application1 =
                createApplication(
                        100L,
                        "Backend"
                );

        Application application2 =
                createApplication(
                        200L,
                        "Frontend"
                );

        Version version =
                createVersion(
                        application1,
                        BuildStatus.SUCCESS
                );

        Environment environment =
                createEnvironment(
                        application2
                );

        KubernetesApplicationRequest kubernetesConfig =
                new KubernetesApplicationRequest(
                        "sd-dev-ecommerce",
                        null,
                        new KubernetesComponentRequest(
                                "ecommerce-backend",
                                "localhost:5000/ecommerce-backend:v1",
                                5,
                                8080
                        ),
                        null,
                        null
                );

        CreateDeploymentRequest request =
                new CreateDeploymentRequest(
                        1L,
                        2L,
                        kubernetesConfig
                );

        when(
                versionRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(
                Optional.of(version)
        );

        when(
                environmentRepository
                        .findByIdAndDeletedFalse(2L)
        ).thenReturn(
                Optional.of(environment)
        );

        DeploymentValidationException exception =
                assertThrows(
                        DeploymentValidationException.class,
                        () ->
                                deploymentService.create(
                                        request,
                                        createUser()
                                )
                );

        assertTrue(
                exception
                        .getMessage()
                        .contains(
                                "même application"
                        )
        );

        verify(
                deploymentRepository,
                never()
        ).saveAndFlush(
                any(Deployment.class)
        );

        verifyNoInteractions(
                eventPublisher
        );
    }

    @Test
    void create_shouldThrowWhenVersionBuildIsNotSuccessful() {

        Application application =
                createApplication(
                        100L,
                        "SmartDeploy Backend"
                );

        Version version =
                createVersion(
                        application,
                        BuildStatus.FAILED
                );

        Environment environment =
                createEnvironment(
                        application
                );

        CreateDeploymentRequest request =
                new CreateDeploymentRequest(
                        1L,
                        2L,
                        null
                );

        when(
                versionRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(
                Optional.of(version)
        );

        when(
                environmentRepository
                        .findByIdAndDeletedFalse(2L)
        ).thenReturn(
                Optional.of(environment)
        );

        DeploymentValidationException exception =
                assertThrows(
                        DeploymentValidationException.class,
                        () ->
                                deploymentService.create(
                                        request,
                                        createUser()
                                )
                );

        assertTrue(
                exception
                        .getMessage()
                        .contains(
                                "build réussi"
                        )
        );

        verify(
                deploymentRepository,
                never()
        ).saveAndFlush(
                any(Deployment.class)
        );

        verifyNoInteractions(
                eventPublisher
        );
    }

    @Test
    void create_shouldThrowWhenDeploymentIsAlreadyRunning() {

        Application application =
                createApplication(
                        100L,
                        "SmartDeploy Backend"
                );

        Version version =
                createVersion(
                        application,
                        BuildStatus.SUCCESS
                );

        Environment environment =
                createEnvironment(
                        application
                );

        KubernetesApplicationRequest kubernetesConfig =
                new KubernetesApplicationRequest(
                        "sd-dev-ecommerce",
                        null,
                        new KubernetesComponentRequest(
                                "ecommerce-backend",
                                "localhost:5000/ecommerce-backend:v1",
                                5,
                                8080
                        ),
                        null,
                        null
                );

        CreateDeploymentRequest request =
                new CreateDeploymentRequest(
                        1L,
                        2L,
                        kubernetesConfig
                );

        when(
                versionRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(
                Optional.of(version)
        );

        when(
                environmentRepository
                        .findByIdAndDeletedFalse(2L)
        ).thenReturn(
                Optional.of(environment)
        );

        when(
                deploymentRepository
                        .existsByEnvironmentAndStatusInAndDeletedFalse(
                                eq(environment),
                                any()
                        )
        ).thenReturn(true);

        assertThrows(
                DeploymentStateException.class,
                () ->
                        deploymentService.create(
                                request,
                                createUser()
                        )
        );

        verify(
                deploymentRepository,
                never()
        ).saveAndFlush(
                any(Deployment.class)
        );

        verifyNoInteractions(
                eventPublisher
        );
    }

    /* ==========================================================
     *                  FIND DEPLOYMENT
     * ==========================================================
     */

    @Test
    void findById_shouldReturnDeployment() {

        Deployment deployment =
                createDeployment(
                        DeploymentStatus.PENDING
                );

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(deployment)
        );

        DeploymentResponse response =
                deploymentService.findById(
                        10L
                );

        assertNotNull(response);

        assertEquals(
                10L,
                response.id()
        );

        assertEquals(
                DeploymentStatus.PENDING,
                response.status()
        );

        assertEquals(
                "SmartDeploy Backend",
                response.applicationName()
        );
    }

    @Test
    void findById_shouldThrowWhenDeploymentDoesNotExist() {

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(999L)
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                DeploymentNotFoundException.class,
                () ->
                        deploymentService.findById(
                                999L
                        )
        );
    }

    /* ==========================================================
     *                  STATUS TRANSITIONS
     * ==========================================================
     */

    @Test
    void updateStatus_shouldMoveFromPendingToRunning() {

        Deployment deployment =
                createDeployment(
                        DeploymentStatus.PENDING
                );

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(deployment)
        );

        when(
                deploymentRepository.saveAndFlush(
                        deployment
                )
        ).thenReturn(
                deployment
        );

        UpdateDeploymentStatusRequest request =
                new UpdateDeploymentStatusRequest(
                        DeploymentStatus.RUNNING,
                        null
                );

        DeploymentResponse response =
                deploymentService.updateStatus(
                        10L,
                        request
                );

        assertEquals(
                DeploymentStatus.RUNNING,
                deployment.getStatus()
        );

        assertNotNull(
                deployment.getStartedAt()
        );

        assertNull(
                deployment.getFinishedAt()
        );

        assertNull(
                deployment.getDurationSeconds()
        );

        assertNull(
                deployment.getErrorMessage()
        );

        assertEquals(
                DeploymentStatus.RUNNING,
                response.status()
        );

        verify(
                deploymentRepository
        ).saveAndFlush(
                deployment
        );
    }

    @Test
    void updateStatus_shouldCompleteSuccessfulDeployment() {

        Deployment deployment =
                createDeployment(
                        DeploymentStatus.RUNNING
                );

        deployment.setStartedAt(
                LocalDateTime.now()
                        .minusSeconds(30)
        );

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(deployment)
        );

        when(
                deploymentRepository.saveAndFlush(
                        deployment
                )
        ).thenReturn(
                deployment
        );

        UpdateDeploymentStatusRequest request =
                new UpdateDeploymentStatusRequest(
                        DeploymentStatus.SUCCESS,
                        null
                );

        DeploymentResponse response =
                deploymentService.updateStatus(
                        10L,
                        request
                );

        assertEquals(
                DeploymentStatus.SUCCESS,
                deployment.getStatus()
        );

        assertNotNull(
                deployment.getFinishedAt()
        );

        assertNotNull(
                deployment.getDurationSeconds()
        );

        assertTrue(
                deployment.getDurationSeconds()
                        >= 0
        );

        assertNull(
                deployment.getErrorMessage()
        );

        assertEquals(
                DeploymentStatus.SUCCESS,
                response.status()
        );
    }

    @Test
    void updateStatus_shouldFailDeploymentWithErrorMessage() {

        Deployment deployment =
                createDeployment(
                        DeploymentStatus.RUNNING
                );

        deployment.setStartedAt(
                LocalDateTime.now()
                        .minusSeconds(15)
        );

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(deployment)
        );

        when(
                deploymentRepository.saveAndFlush(
                        deployment
                )
        ).thenReturn(
                deployment
        );

        UpdateDeploymentStatusRequest request =
                new UpdateDeploymentStatusRequest(
                        DeploymentStatus.FAILED,
                        " Pod CrashLoopBackOff "
                );

        DeploymentResponse response =
                deploymentService.updateStatus(
                        10L,
                        request
                );

        assertEquals(
                DeploymentStatus.FAILED,
                deployment.getStatus()
        );

        assertEquals(
                "Pod CrashLoopBackOff",
                deployment.getErrorMessage()
        );

        assertNotNull(
                deployment.getFinishedAt()
        );

        assertNotNull(
                deployment.getDurationSeconds()
        );

        assertEquals(
                "Pod CrashLoopBackOff",
                response.errorMessage()
        );
    }

    @Test
    void updateStatus_shouldThrowWhenFailedStatusHasNoErrorMessage() {

        Deployment deployment =
                createDeployment(
                        DeploymentStatus.RUNNING
                );

        deployment.setStartedAt(
                LocalDateTime.now()
                        .minusSeconds(10)
        );

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(deployment)
        );

        UpdateDeploymentStatusRequest request =
                new UpdateDeploymentStatusRequest(
                        DeploymentStatus.FAILED,
                        "   "
                );

        assertThrows(
                DeploymentValidationException.class,
                () ->
                        deploymentService.updateStatus(
                                10L,
                                request
                        )
        );

        verify(
                deploymentRepository,
                never()
        ).saveAndFlush(
                any(Deployment.class)
        );
    }

    @Test
    void updateStatus_shouldThrowForInvalidTransition() {

        Deployment deployment =
                createDeployment(
                        DeploymentStatus.PENDING
                );

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(deployment)
        );

        UpdateDeploymentStatusRequest request =
                new UpdateDeploymentStatusRequest(
                        DeploymentStatus.SUCCESS,
                        null
                );

        assertThrows(
                DeploymentStateException.class,
                () ->
                        deploymentService.updateStatus(
                                10L,
                                request
                        )
        );

        verify(
                deploymentRepository,
                never()
        ).saveAndFlush(
                any(Deployment.class)
        );
    }

    @Test
    void updateStatus_shouldThrowWhenStatusIsUnchanged() {

        Deployment deployment =
                createDeployment(
                        DeploymentStatus.PENDING
                );

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(deployment)
        );

        UpdateDeploymentStatusRequest request =
                new UpdateDeploymentStatusRequest(
                        DeploymentStatus.PENDING,
                        null
                );

        assertThrows(
                DeploymentStateException.class,
                () ->
                        deploymentService.updateStatus(
                                10L,
                                request
                        )
        );

        verify(
                deploymentRepository,
                never()
        ).saveAndFlush(
                any(Deployment.class)
        );
    }

    /* ==========================================================
     *                  JENKINS METADATA
     * ==========================================================
     */

    @Test
    void updateMetadata_shouldUpdateJenkinsInformation() {

        Deployment deployment =
                createDeployment(
                        DeploymentStatus.RUNNING
                );

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(deployment)
        );

        when(
                deploymentRepository.saveAndFlush(
                        deployment
                )
        ).thenReturn(
                deployment
        );

        UpdateDeploymentMetadataRequest request =
                new UpdateDeploymentMetadataRequest(
                        42,
                        " https://jenkins/job/smartdeploy/42 ",
                        "smartdeploy-demo"
                );

        DeploymentResponse response =
                deploymentService.updateMetadata(
                        10L,
                        request
                );

        assertEquals(
                42,
                deployment.getJenkinsBuildNumber()
        );

        assertEquals(
                "https://jenkins/job/smartdeploy/42",
                deployment.getDeploymentLogsUrl()
        );

        assertEquals(
                42,
                response.jenkinsBuildNumber()
        );
    }

    /* ==========================================================
     *                  ROLLBACK
     * ==========================================================
     */

    @Test
    void createRollback_shouldCreatePendingRollback() {

        Deployment original =
                createDeployment(
                        DeploymentStatus.SUCCESS
                );
        original.setDeployedBackendImage(
                "localhost:5000/app-backend@sha256:" + "a".repeat(64)
        );
        DeploymentKubernetesConfig originalConfig = new DeploymentKubernetesConfig();
        originalConfig.setNamespace("sd-dev-app");
        originalConfig.setConfigurationJson("{}");
        when(kubernetesConfigRepository.findByDeploymentId(10L))
                .thenReturn(Optional.of(originalConfig));

        User currentUser =
                createUser();

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(original)
        );

        when(
                deploymentRepository
                        .existsByEnvironmentAndStatusInAndDeletedFalse(
                                eq(
                                        original.getEnvironment()
                                ),
                                any()
                        )
        ).thenReturn(false);

        when(
                deploymentRepository.saveAndFlush(
                        any(Deployment.class)
                )
        ).thenAnswer(invocation -> {

            Deployment rollback =
                    invocation.getArgument(0);

            rollback.setId(11L);

            rollback.setCreatedAt(
                    LocalDateTime.now()
            );

            rollback.setUpdatedAt(
                    LocalDateTime.now()
            );

            return rollback;
        });

        DeploymentResponse response =
                deploymentService.createRollback(
                        10L,
                        currentUser
                );

        assertEquals(
                11L,
                response.id()
        );

        assertEquals(
                DeploymentStatus.PENDING,
                response.status()
        );

        assertEquals(
                10L,
                response.rollbackOfId()
        );

        // La configuration est copiée et le pipeline est réellement déclenché.
        verify(kubernetesConfigRepository).saveAndFlush(any(DeploymentKubernetesConfig.class));
        verify(eventPublisher).publishEvent(new DeploymentPipelineRequestedEvent(11L));

        verify(
                deploymentRepository
        ).saveAndFlush(
                any(Deployment.class)
        );
    }

    @Test
    void createRollback_shouldRejectDeploymentWithoutDigest() {
        Deployment original = createDeployment(DeploymentStatus.SUCCESS);
        original.setDeployedBackendImage("localhost:5000/app-backend:1.0.0");
        when(deploymentRepository.findByIdAndDeletedFalse(10L))
                .thenReturn(Optional.of(original));

        assertThrows(
                DeploymentStateException.class,
                () -> deploymentService.createRollback(10L, createUser())
        );
        verify(deploymentRepository, never()).saveAndFlush(any(Deployment.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void applyPipelineCallback_shouldMarkReplacedDeploymentAfterSuccessfulRollback() {
        Deployment target = createDeployment(DeploymentStatus.SUCCESS);
        target.setId(5L);
        Deployment current = createDeployment(DeploymentStatus.SUCCESS);
        current.setId(9L);
        Deployment rollback = createDeployment(DeploymentStatus.RUNNING);
        rollback.setRollbackOf(target);
        rollback.setJenkinsBuildNumber(60);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(rollback));
        when(deploymentRepository.findByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(
                rollback.getEnvironment(), DeploymentStatus.SUCCESS))
                .thenReturn(List.of(rollback, current, target));

        deploymentService.applyPipelineCallback(
                10L, DeploymentStatus.SUCCESS, 60, null, null, null
        );

        assertEquals(DeploymentStatus.ROLLED_BACK, current.getStatus());
        assertEquals(DeploymentStatus.SUCCESS, target.getStatus());
    }

    @Test
    void expireStaleDeployments_shouldFailOnlyDeploymentsBeyondTheirDelay() {
        LocalDateTime now = LocalDateTime.now();
        Deployment stalePending = createDeployment(DeploymentStatus.PENDING);
        stalePending.setId(21L);
        stalePending.setCreatedAt(now.minusMinutes(45));
        Deployment recentRunning = createDeployment(DeploymentStatus.RUNNING);
        recentRunning.setId(22L);
        recentRunning.setStartedAt(now.minusMinutes(10));
        when(deploymentRepository.findByStatusInAndDeletedFalse(any()))
                .thenReturn(List.of(stalePending, recentRunning));
        when(deploymentRepository.findActiveByIdForUpdate(21L)).thenReturn(Optional.of(stalePending));
        when(deploymentRepository.findActiveByIdForUpdate(22L)).thenReturn(Optional.of(recentRunning));

        int expired = deploymentService.expireStaleDeployments(
                now, java.time.Duration.ofMinutes(30), java.time.Duration.ofMinutes(120)
        );

        assertEquals(1, expired);
        assertEquals(DeploymentStatus.FAILED, stalePending.getStatus());
        assertTrue(stalePending.getErrorMessage().startsWith("Délai dépassé."));
        assertEquals(DeploymentStatus.RUNNING, recentRunning.getStatus());
    }

    @Test
    void createRollback_shouldThrowWhenOriginalDeploymentIsNotSuccessful() {

        Deployment original =
                createDeployment(
                        DeploymentStatus.FAILED
                );

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(original)
        );

        assertThrows(
                DeploymentStateException.class,
                () ->
                        deploymentService.createRollback(
                                10L,
                                createUser()
                        )
        );

        verify(
                deploymentRepository,
                never()
        ).saveAndFlush(
                any(Deployment.class)
        );
    }

    /* ==========================================================
     *                  SOFT DELETE
     * ==========================================================
     */

    @Test
    void softDelete_shouldMarkDeploymentDeleted() {

        Deployment deployment =
                createDeployment(
                        DeploymentStatus.SUCCESS
                );

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(deployment)
        );

        when(
                deploymentRepository.saveAndFlush(
                        deployment
                )
        ).thenReturn(
                deployment
        );

        deploymentService.softDelete(
                10L
        );

        assertTrue(
                deployment.isDeleted()
        );

        verify(
                deploymentRepository
        ).saveAndFlush(
                deployment
        );
    }

    @Test
    void softDelete_shouldThrowWhenDeploymentIsRunning() {

        Deployment deployment =
                createDeployment(
                        DeploymentStatus.RUNNING
                );

        when(
                deploymentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(
                Optional.of(deployment)
        );

        assertThrows(
                DeploymentStateException.class,
                () ->
                        deploymentService.softDelete(
                                10L
                        )
        );

        verify(
                deploymentRepository,
                never()
        ).saveAndFlush(
                any(Deployment.class)
        );
    }

    /* ==========================================================
     *              JENKINS PIPELINE CALLBACK
     * ==========================================================
     */

    @Test
    void applyPipelineCallback_shouldMoveFromPendingToRunningAndStoreMetadata() {
        Deployment deployment = createDeployment(DeploymentStatus.PENDING);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        PipelineCallbackResult result = deploymentService.applyPipelineCallback(
                10L, DeploymentStatus.RUNNING, 44,
                "http://jenkins/job/smartdeploy/44/", null, null
        );

        assertEquals(PipelineCallbackOutcome.APPLIED, result.outcome());
        assertEquals(DeploymentStatus.RUNNING, deployment.getStatus());
        assertEquals(44, deployment.getJenkinsBuildNumber());
        assertEquals("http://jenkins/job/smartdeploy/44/", deployment.getDeploymentLogsUrl());
        assertNotNull(deployment.getStartedAt());
        verify(deploymentRepository).saveAndFlush(deployment);
    }

    @Test
    void applyPipelineCallback_shouldTreatRepeatedStatusAsDuplicate() {
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.setJenkinsBuildNumber(44);
        LocalDateTime startedAt = LocalDateTime.now().minusMinutes(2);
        deployment.setStartedAt(startedAt);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        PipelineCallbackResult result = deploymentService.applyPipelineCallback(
                10L, DeploymentStatus.RUNNING, 44, null, null, null
        );

        assertEquals(PipelineCallbackOutcome.DUPLICATE, result.outcome());
        assertEquals(DeploymentStatus.RUNNING, deployment.getStatus());
        // Un doublon ne réinitialise pas la date de démarrage.
        assertEquals(startedAt, deployment.getStartedAt());
    }

    @Test
    void applyPipelineCallback_shouldAcceptSuccessWhenRunningCallbackWasLost() {
        Deployment deployment = createDeployment(DeploymentStatus.PENDING);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        PipelineCallbackResult result = deploymentService.applyPipelineCallback(
                10L, DeploymentStatus.SUCCESS, 44, null, null, "ecommerce-backend"
        );

        assertEquals(PipelineCallbackOutcome.APPLIED, result.outcome());
        assertEquals(DeploymentStatus.SUCCESS, deployment.getStatus());
        assertNotNull(deployment.getFinishedAt());
        assertEquals("ecommerce-backend", deployment.getKubernetesDeploymentName());
    }

    @Test
    void applyPipelineCallback_shouldIgnoreLateCallbackOnFinishedDeployment() {
        Deployment deployment = createDeployment(DeploymentStatus.SUCCESS);
        deployment.setKubernetesDeploymentName("ecommerce-backend");
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        PipelineCallbackResult result = deploymentService.applyPipelineCallback(
                10L, DeploymentStatus.RUNNING, null, null, null, null
        );

        assertEquals(PipelineCallbackOutcome.IGNORED, result.outcome());
        assertEquals(DeploymentStatus.SUCCESS, deployment.getStatus());
        verify(deploymentRepository, never()).saveAndFlush(any(Deployment.class));
    }

    @Test
    void applyPipelineCallback_shouldKeepKnownMetadataWhenCallbackSendsBlanks() {
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.setJenkinsBuildNumber(44);
        deployment.setDeploymentLogsUrl("http://jenkins/job/smartdeploy/44/");
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        deploymentService.applyPipelineCallback(
                10L, DeploymentStatus.FAILED, 44, "  ", null, ""
        );

        assertEquals(DeploymentStatus.FAILED, deployment.getStatus());
        assertEquals("http://jenkins/job/smartdeploy/44/", deployment.getDeploymentLogsUrl());
        assertNull(deployment.getKubernetesDeploymentName());
        assertEquals("Échec signalé par Jenkins sans détail", deployment.getErrorMessage());
    }

    @Test
    void applyPipelineCallback_shouldMarkPendingVersionAsBuiltOnSuccess() {
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.getVersion().setBuildStatus(BuildStatus.PENDING);
        deployment.setJenkinsBuildNumber(44);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        deploymentService.applyPipelineCallback(
                10L, DeploymentStatus.SUCCESS, 44,
                "http://jenkins/job/smartdeploy/44/", null, "ecommerce-backend"
        );

        Version version = deployment.getVersion();
        assertEquals(BuildStatus.SUCCESS, version.getBuildStatus());
        assertEquals(44, version.getBuildNumber());
        assertEquals("http://jenkins/job/smartdeploy/44/", version.getBuildLogsUrl());
        verify(versionRepository).saveAndFlush(version);
    }

    @Test
    void applyPipelineCallback_shouldKeepVersionDeployableWhenPipelineFails() {
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.getVersion().setBuildStatus(BuildStatus.PENDING);
        deployment.setJenkinsBuildNumber(44);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        deploymentService.applyPipelineCallback(
                10L, DeploymentStatus.FAILED, 44, null, "Rollout timeout", null
        );

        assertEquals(BuildStatus.PENDING, deployment.getVersion().getBuildStatus());
        verify(versionRepository, never()).saveAndFlush(any(Version.class));
    }

    @Test
    void applyPipelineCallback_shouldNotTouchVersionForIgnoredCallback() {
        Deployment deployment = createDeployment(DeploymentStatus.FAILED);
        deployment.getVersion().setBuildStatus(BuildStatus.PENDING);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        PipelineCallbackResult result = deploymentService.applyPipelineCallback(
                10L, DeploymentStatus.SUCCESS, null, null, null, null
        );

        assertEquals(PipelineCallbackOutcome.IGNORED, result.outcome());
        assertEquals(BuildStatus.PENDING, deployment.getVersion().getBuildStatus());
        verify(versionRepository, never()).saveAndFlush(any(Version.class));
    }

    @Test
    void applyPipelineCallback_shouldRejectRunningCallbackOnCancelledDeployment() {
        Deployment deployment = createDeployment(DeploymentStatus.CANCELLED);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        assertThrows(
                DeploymentStateException.class,
                () -> deploymentService.applyPipelineCallback(
                        10L, DeploymentStatus.RUNNING, 47, null, null, null
                )
        );
        assertEquals(DeploymentStatus.CANCELLED, deployment.getStatus());
    }

    @Test
    void markCancelled_shouldCancelRunningDeploymentWithReason() {
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.setStartedAt(LocalDateTime.now().minusSeconds(30));
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));
        when(deploymentRepository.saveAndFlush(deployment)).thenReturn(deployment);

        DeploymentResponse response = deploymentService.markCancelled(
                10L, createUser(), "Build Jenkins #46 arrêté."
        );

        assertEquals(DeploymentStatus.CANCELLED, response.status());
        assertNotNull(deployment.getFinishedAt());
        assertTrue(deployment.getErrorMessage().startsWith("Annulé par "));
        assertTrue(deployment.getErrorMessage().endsWith("Build Jenkins #46 arrêté."));
    }

    @Test
    void markCancelled_shouldRejectDeploymentFinishedMeanwhile() {
        Deployment deployment = createDeployment(DeploymentStatus.SUCCESS);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        assertThrows(
                DeploymentStateException.class,
                () -> deploymentService.markCancelled(10L, createUser(), null)
        );
        assertEquals(DeploymentStatus.SUCCESS, deployment.getStatus());
        verify(deploymentRepository, never()).saveAndFlush(any(Deployment.class));
    }

    @Test
    void applyPipelineCallback_shouldStoreDeployedImagesAndPinThemOnVersion() {
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.setJenkinsBuildNumber(55);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        String backend = "localhost:5000/fullstack-demo-backend@sha256:"
                + "3f9a0c7e1b2d4a6f8e0c2b4d6f8a0c2e4b6d8f0a2c4e6b8d0f2a4c6e8b0d2f4a";
        String frontend = "localhost:5000/fullstack-demo-frontend@sha256:"
                + "aa11bb22cc33dd44ee55ff6677889900aabbccddeeff00112233445566778899";

        deploymentService.applyPipelineCallback(
                10L, DeploymentStatus.SUCCESS, 55, null, null,
                "fullstack-demo-backend", backend, frontend
        );

        assertEquals(backend, deployment.getDeployedBackendImage());
        assertEquals(frontend, deployment.getDeployedFrontendImage());
        assertEquals(backend, deployment.getVersion().getDockerImage());
    }

    @Test
    void applyPipelineCallback_shouldIgnoreInvalidImageReference() {
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.setJenkinsBuildNumber(55);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        deploymentService.applyPipelineCallback(
                10L, DeploymentStatus.RUNNING, 55, null, null, null,
                "image avec espaces; rm -rf /", null
        );

        assertNull(deployment.getDeployedBackendImage());
    }

    @Test
    void normalizeImageReference_shouldAcceptTagsAndDigests() {
        assertEquals("localhost:5000/app:1.0.0",
                DeploymentService.normalizeImageReference(" localhost:5000/app:1.0.0 "));
        assertEquals("localhost:5000/app@sha256:abc123",
                DeploymentService.normalizeImageReference("localhost:5000/app@sha256:abc123"));
        assertNull(DeploymentService.normalizeImageReference(""));
        assertNull(DeploymentService.normalizeImageReference("bad image"));
    }

    @Test
    void applyPipelineCallback_shouldRejectCallbackFromAnotherBuild() {
        Deployment deployment = createDeployment(DeploymentStatus.RUNNING);
        deployment.setJenkinsBuildNumber(44);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        assertThrows(
                DeploymentStateException.class,
                () -> deploymentService.applyPipelineCallback(
                        10L, DeploymentStatus.SUCCESS, 45, null, null, null
                )
        );
        assertEquals(DeploymentStatus.RUNNING, deployment.getStatus());
    }

    @Test
    void applyPipelineCallback_shouldRejectForbiddenStatus() {
        assertThrows(
                DeploymentValidationException.class,
                () -> deploymentService.applyPipelineCallback(
                        10L, DeploymentStatus.ROLLED_BACK, 44, null, null, null
                )
        );
        verifyNoInteractions(deploymentRepository);
    }

    @Test
    void applyPipelineCallback_shouldThrowWhenDeploymentDoesNotExist() {
        when(deploymentRepository.findActiveByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                DeploymentNotFoundException.class,
                () -> deploymentService.applyPipelineCallback(
                        99L, DeploymentStatus.RUNNING, 1, null, null, null
                )
        );
    }

    /* ==========================================================
     *                  TEST HELPERS
     * ==========================================================
     */

    private Application createApplication(
            Long id,
            String name
    ) {
        LocalDateTime now =
                LocalDateTime.now();

        return Application.builder()
                .id(id)
                .name(name)
                .description(
                        "Application description"
                )
                .repositoryUrl(
                        "https://github.com/company/application"
                )
                .technology(
                        Technology.SPRING_BOOT
                )
                .active(true)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Version createVersion(
            Application application,
            BuildStatus status
    ) {
        LocalDateTime now =
                LocalDateTime.now();

        return Version.builder()
                .id(1L)
                .application(application)
                .version("1.0.0")
                .gitBranch("main")
                .gitCommit("abc123")
                .dockerImage(
                        "smartdeploy/backend:1.0.0"
                )
                .buildStatus(status)
                .buildNumber(10)
                .artifactPath(
                        "target/backend.jar"
                )
                .buildLogsUrl(
                        "https://jenkins/build/10"
                )
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Environment createEnvironment(
            Application application
    ) {
        LocalDateTime now =
                LocalDateTime.now();

        return Environment.builder()
                .id(2L)
                .application(application)
                .type(
                        EnvironmentType.PRODUCTION
                )
                .name("Production")
                .namespace(
                        "smartdeploy-prod"
                )
                .clusterUrl(
                        "https://cluster.example.com"
                )
                .active(true)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private User createUser() {
        LocalDateTime now =
                LocalDateTime.now();

        return User.builder()
                .id(3L)
                .firstName("Ruqaia")
                .lastName("Sabta")
                .email(
                        "ruqaia@smartdeploy.com"
                )
                .password(
                        "encoded-password"
                )
                .role(Role.DEVOPS)
                .enabled(true)
                .deleted(false)
                .tokenVersion(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Deployment createDeployment(
            DeploymentStatus status
    ) {
        LocalDateTime now =
                LocalDateTime.now();

        Application application =
                createApplication(
                        100L,
                        "SmartDeploy Backend"
                );

        return Deployment.builder()
                .id(10L)
                .version(
                        createVersion(
                                application,
                                BuildStatus.SUCCESS
                        )
                )
                .environment(
                        createEnvironment(
                                application
                        )
                )
                .requestedBy(
                        createUser()
                )
                .status(status)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
