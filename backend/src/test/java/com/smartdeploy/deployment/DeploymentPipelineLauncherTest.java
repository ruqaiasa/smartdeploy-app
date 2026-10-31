package com.smartdeploy.deployment;

import com.smartdeploy.integration.jenkins.JenkinsBuildResponse;
import com.smartdeploy.integration.jenkins.JenkinsIntegrationException;
import com.smartdeploy.integration.jenkins.JenkinsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private DeploymentPipelineLauncher launcher;

    @BeforeEach
    void setUp() {
        launcher = new DeploymentPipelineLauncher(
                deploymentRepository,
                jenkinsService
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
        verify(deploymentRepository, never()).saveAndFlush(any(Deployment.class));
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

    private Deployment deployment(DeploymentStatus status) {
        return Deployment.builder()
                .id(10L)
                .status(status)
                .deleted(false)
                .build();
    }
}
