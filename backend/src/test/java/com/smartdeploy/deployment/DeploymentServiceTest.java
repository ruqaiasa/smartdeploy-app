package com.smartdeploy.deployment;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.Technology;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.environment.EnvironmentType;
import com.smartdeploy.integration.jenkins.JenkinsService;
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

import java.time.LocalDateTime;
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
    private JenkinsService jenkinsService;

    private DeploymentService deploymentService;

    @BeforeEach
    void setUp() {
        deploymentService = new DeploymentService(
                deploymentRepository,
                versionRepository,
                environmentRepository,
                jenkinsService
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

        CreateDeploymentRequest request =
                new CreateDeploymentRequest(
                        1L,
                        2L
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

        verify(
                jenkinsService
        ).triggerDeployment(
                any(Deployment.class)
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

        CreateDeploymentRequest request =
                new CreateDeploymentRequest(
                        1L,
                        2L
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
                jenkinsService
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
                        2L
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
                                "build est réussi"
                        )
        );

        verify(
                deploymentRepository,
                never()
        ).saveAndFlush(
                any(Deployment.class)
        );

        verifyNoInteractions(
                jenkinsService
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

        CreateDeploymentRequest request =
                new CreateDeploymentRequest(
                        1L,
                        2L
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
                jenkinsService
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
                        " https://jenkins/job/smartdeploy/42 "
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

        verify(
                deploymentRepository
        ).saveAndFlush(
                any(Deployment.class)
        );
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