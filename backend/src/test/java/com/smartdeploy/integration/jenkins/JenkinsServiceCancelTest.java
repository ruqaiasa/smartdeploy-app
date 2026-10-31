package com.smartdeploy.integration.jenkins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdeploy.deployment.DeploymentKubernetesConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JenkinsServiceCancelTest {

    @Mock
    private JenkinsClient jenkinsClient;

    @Mock
    private DeploymentKubernetesConfigRepository kubernetesConfigRepository;

    private JenkinsService jenkinsService;

    @BeforeEach
    void setUp() {
        jenkinsService = new JenkinsService(jenkinsClient, kubernetesConfigRepository, new ObjectMapper());
    }

    @Test
    void shouldStopKnownBuild() {
        when(jenkinsClient.stopBuild(46)).thenReturn(true);

        JenkinsCancellationResult result = jenkinsService.cancelPipeline("http://jenkins/queue/item/9/", 46);

        assertTrue(result.jenkinsReached());
        assertTrue(result.message().contains("#46"));
        verify(jenkinsClient, never()).cancelQueueItem(anyLong());
    }

    @Test
    void shouldCancelItemStillWaitingInQueue() {
        when(jenkinsClient.getQueueItemJson(9L)).thenReturn(json("{'id':9,'why':'En attente'}"));
        when(jenkinsClient.cancelQueueItem(9L)).thenReturn(true);

        JenkinsCancellationResult result = jenkinsService.cancelPipeline("http://192.168.85.129:8080/queue/item/9/", null);

        assertTrue(result.jenkinsReached());
        verify(jenkinsClient).cancelQueueItem(9L);
        verify(jenkinsClient, never()).stopBuild(anyInt());
    }

    @Test
    void shouldStopBuildThatJustLeftTheQueue() {
        when(jenkinsClient.getQueueItemJson(9L)).thenReturn(json("{'id':9,'executable':{'number':47}}"));
        when(jenkinsClient.stopBuild(47)).thenReturn(true);

        jenkinsService.cancelPipeline("http://jenkins/queue/item/9/", null);

        verify(jenkinsClient).stopBuild(47);
        verify(jenkinsClient, never()).cancelQueueItem(anyLong());
    }

    @Test
    void shouldDoNothingWhenDeploymentNeverReachedJenkins() {
        JenkinsCancellationResult result = jenkinsService.cancelPipeline(null, null);

        assertTrue(result.jenkinsReached());
        verifyNoInteractions(jenkinsClient);
    }

    @Test
    void shouldReportUnreachableJenkinsWithoutThrowing() {
        when(jenkinsClient.stopBuild(46)).thenThrow(new RuntimeException("Connection refused"));

        JenkinsCancellationResult result = jenkinsService.cancelPipeline(null, 46);

        assertFalse(result.jenkinsReached());
        assertTrue(result.message().contains("Connection refused"));
    }

    /** JSON écrit avec des apostrophes pour la lisibilité. */
    private static String json(String singleQuoted) {
        return singleQuoted.replace('\'', '"');
    }

    @Test
    void shouldExtractQueueIdFromJenkinsUrl() {
        assertEquals(123L, JenkinsService.extractQueueId("http://192.168.85.129:8080/queue/item/123/"));
        assertNull(JenkinsService.extractQueueId("http://jenkins/job/smartdeploy/"));
        assertNull(JenkinsService.extractQueueId(null));
    }
}
