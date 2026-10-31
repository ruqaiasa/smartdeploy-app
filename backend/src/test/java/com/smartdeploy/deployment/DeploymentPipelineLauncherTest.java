package com.smartdeploy.deployment;

import com.smartdeploy.integration.jenkins.JenkinsBuildResponse;
import com.smartdeploy.integration.jenkins.JenkinsCancellationResult;
import com.smartdeploy.integration.jenkins.JenkinsIntegrationException;
import com.smartdeploy.integration.jenkins.JenkinsService;
import com.smartdeploy.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentPipelineLauncherTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private JenkinsService jenkinsService;

    @Mock
    private DeploymentService deploymentService;

    private DeploymentPipelineLauncher launcher;

    @BeforeEach
    void setUp() {
        launcher = new DeploymentPipelineLauncher(
                deploymentRepository,
                jenkinsService,
                deploymentService
        );
    }

    @Test
    void shouldTriggerJenkinsForPendingDeployment() {
        Deployment deployment = deployment(DeploymentStatus.PENDING);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));
        when(jenkinsService.triggerDeployment(deployment))
                .thenReturn(new JenkinsBuildResponse(
                        "http://jenkins/queue/item/1/", "ok"
                ));

        launcher.onPipelineRequested(new DeploymentPipelineRequestedEvent(10L));

        verify(jenkinsService).triggerDeployment(deployment);
        assertEquals(DeploymentStatus.PENDING, deployment.getStatus());
        // L'URL de file d'attente est conservée pour pouvoir annuler.
        assertEquals("http://jenkins/queue/item/1/", deployment.getJenkinsQueueUrl());
        verify(deploymentRepository).saveAndFlush(deployment);
    }

    @Test
    void shouldMarkDeploymentFailedWhenJenkinsRejectsTrigger() {
        Deployment deployment = deployment(DeploymentStatus.PENDING);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));
        when(jenkinsService.triggerDeployment(deployment))
                .thenThrow(new JenkinsIntegrationException("Statut HTTP : 401"));

        launcher.onPipelineRequested(new DeploymentPipelineRequestedEvent(10L));

        assertEquals(DeploymentStatus.FAILED, deployment.getStatus());
        assertNotNull(deployment.getFinishedAt());
        assertTrue(deployment.getErrorMessage().contains("Statut HTTP : 401"));
        verify(deploymentRepository).saveAndFlush(deployment);
    }

    @Test
    void shouldNotTriggerJenkinsWhenDeploymentIsNoLongerPending() {
        Deployment deployment = deployment(DeploymentStatus.CANCELLED);
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(deployment));

        launcher.onPipelineRequested(new DeploymentPipelineRequestedEvent(10L));

        verifyNoInteractions(jenkinsService);
    }

    @Test
    void shouldIgnoreMissingDeployment() {
        when(deploymentRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.empty());

        launcher.onPipelineRequested(new DeploymentPipelineRequestedEvent(10L));

        verifyNoInteractions(jenkinsService);
    }

    @Test
    void cancel_shouldStopJenkinsThenMarkDeploymentCancelled() {
        Deployment deployment = deployment(DeploymentStatus.RUNNING);
        deployment.setJenkinsBuildNumber(46);
        deployment.setJenkinsQueueUrl("http://jenkins/queue/item/9/");
        User user = new User();
        when(deploymentRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(deployment));
        when(jenkinsService.cancelPipeline("http://jenkins/queue/item/9/", 46))
                .thenReturn(new JenkinsCancellationResult(true, "Build Jenkins #46 arrêté."));

        launcher.cancel(10L, user);

        InOrder order = inOrder(deploymentService, jenkinsService);
        order.verify(deploymentService).ensureCancellable(deployment);
        order.verify(jenkinsService).cancelPipeline("http://jenkins/queue/item/9/", 46);
        order.verify(deploymentService).markCancelled(10L, user, "Build Jenkins #46 arrêté.");
    }

    @Test
    void cancel_shouldStillCancelWhenJenkinsIsUnreachable() {
        Deployment deployment = deployment(DeploymentStatus.PENDING);
        User user = new User();
        when(deploymentRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(deployment));
        when(jenkinsService.cancelPipeline(null, null))
                .thenReturn(new JenkinsCancellationResult(false, "Jenkins n'a pas pu être joint"));

        launcher.cancel(10L, user);

        verify(deploymentService).markCancelled(10L, user, "Jenkins n'a pas pu être joint");
    }

    @Test
    void cancel_shouldNotCallJenkinsForFinishedDeployment() {
        Deployment deployment = deployment(DeploymentStatus.SUCCESS);
        when(deploymentRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(deployment));
        doThrow(new DeploymentStateException("terminé"))
                .when(deploymentService).ensureCancellable(deployment);

        assertThrows(DeploymentStateException.class, () -> launcher.cancel(10L, new User()));

        verifyNoInteractions(jenkinsService);
        verify(deploymentService, never()).markCancelled(any(), any(), any());
    }

    private Deployment deployment(DeploymentStatus status) {
        return Deployment.builder()
                .id(10L)
                .status(status)
                .deleted(false)
                .build();
    }
}
