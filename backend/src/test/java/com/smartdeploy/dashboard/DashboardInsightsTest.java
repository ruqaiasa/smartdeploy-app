package com.smartdeploy.dashboard;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.Environment;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tendances du tableau de bord : les calculs. */
class DashboardInsightsTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 20, 10, 0);

    private static Deployment deployment(DeploymentStatus status, long envId, int minuteCreated, Integer minuteFinished, Long seconds) {
        Environment env = mock(Environment.class);
        when(env.getId()).thenReturn(envId);
        Deployment d = mock(Deployment.class);
        when(d.getStatus()).thenReturn(status);
        when(d.getEnvironment()).thenReturn(env);
        when(d.getCreatedAt()).thenReturn(T0.plusMinutes(minuteCreated));
        when(d.getFinishedAt()).thenReturn(minuteFinished == null ? null : T0.plusMinutes(minuteFinished));
        when(d.getDurationSeconds()).thenReturn(seconds);
        return d;
    }

    @Test
    void successRateCountsAReplacedVersionAsASuccess() {
        List<Deployment> list = List.of(deployment(DeploymentStatus.SUCCESS, 1, 0, 5, 300L),
                deployment(DeploymentStatus.ROLLED_BACK, 1, 10, 15, 300L),
                deployment(DeploymentStatus.FAILED, 1, 20, 22, null),
                deployment(DeploymentStatus.AWAITING_APPROVAL, 1, 30, null, null));
        assertEquals(66.7, DashboardInsightsController.successRate(list));
        assertNull(DashboardInsightsController.successRate(List.of()));
    }

    @Test
    void recoveryIsTheDelayFromAFailureToTheNextSuccessInTheSameEnvironment() {
        List<Deployment> list = List.of(deployment(DeploymentStatus.FAILED, 1, 0, 4, null),
                deployment(DeploymentStatus.SUCCESS, 2, 5, 9, 240L),       // autre environnement : ignoré
                deployment(DeploymentStatus.SUCCESS, 1, 20, 28, 480L));
        assertEquals(24.0, DashboardInsightsController.recoveryMinutes(list), "de 10 h 04 à 10 h 28");
    }

    @Test
    void medianHandlesEvenAndOddSizes() {
        assertEquals(5.0, DashboardInsightsController.median(List.of(9.0, 1.0, 5.0)));
        assertEquals(4.0, DashboardInsightsController.median(List.of(2.0, 6.0)));
        assertNull(DashboardInsightsController.median(List.of()));
    }
}
