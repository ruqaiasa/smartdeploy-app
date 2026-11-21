package com.smartdeploy.incident;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.guard.DeploymentGuard;
import com.smartdeploy.guard.DeploymentGuardRepository;
import com.smartdeploy.version.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Mémoire des incidents : ce qui a remis l'environnement en état, et en combien de temps. */
class IncidentMemoryTest {

    private DeploymentRepository deployments;
    private DeploymentGuardRepository guards;
    private IncidentMemory memory;
    private Environment testing;
    private final LocalDateTime t0 = LocalDateTime.of(2026, 11, 16, 22, 50);

    private Deployment deployment(long id, String version, long versionId, DeploymentStatus status, LocalDateTime at, LocalDateTime end) {
        Version v = mock(Version.class);
        when(v.getVersion()).thenReturn(version);
        when(v.getId()).thenReturn(versionId);
        Deployment d = mock(Deployment.class);
        when(d.getId()).thenReturn(id);
        when(d.getVersion()).thenReturn(v);
        when(d.getEnvironment()).thenReturn(testing);
        when(d.getStatus()).thenReturn(status);
        when(d.getCreatedAt()).thenReturn(at);
        when(d.getFinishedAt()).thenReturn(end);
        return d;
    }

    @BeforeEach
    void setUp() {
        deployments = mock(DeploymentRepository.class);
        guards = mock(DeploymentGuardRepository.class);
        memory = new IncidentMemory(mock(IncidentAnalysisRepository.class), deployments, guards);
        testing = mock(Environment.class);
    }

    @Test
    void anAutomaticRollbackLaunchedByTheGuardIsRecognised() {
        Deployment incident = deployment(129, "1.1.1-plantage", 2, DeploymentStatus.ROLLED_BACK, t0, t0.plusMinutes(1));
        Deployment rollback = deployment(130, "1.1.0", 1, DeploymentStatus.SUCCESS, t0.plusMinutes(4), t0.plusMinutes(5));
        DeploymentGuard guard = mock(DeploymentGuard.class);
        when(guard.getAutoRollbackDeploymentId()).thenReturn(130L);
        when(guards.findFirstByDeploymentIdOrderByIdDesc(129L)).thenReturn(Optional.of(guard));
        when(deployments.findById(130L)).thenReturn(Optional.of(rollback));

        IncidentMemory.Resolution r = memory.resolution(incident);

        assertEquals("Retour arrière automatique vers la version 1.1.0", r.description());
        assertEquals(5L, r.minutes());
    }

    @Test
    void otherwiseTheNextSuccessfulDeploymentTellsHowItWasFixed() {
        Deployment incident = deployment(131, "1.1.2-compilation", 3, DeploymentStatus.FAILED, t0, t0.plusMinutes(1));
        Deployment failedAgain = deployment(132, "1.1.2-compilation", 3, DeploymentStatus.FAILED, t0.plusMinutes(10), t0.plusMinutes(11));
        Deployment fixed = deployment(133, "1.1.3", 4, DeploymentStatus.SUCCESS, t0.plusMinutes(30), t0.plusMinutes(32));
        when(guards.findFirstByDeploymentIdOrderByIdDesc(131L)).thenReturn(Optional.empty());
        when(deployments.findTop10ByEnvironmentAndCreatedAtAfterAndDeletedFalseOrderByCreatedAtAsc(eq(testing), any()))
                .thenReturn(List.of(failedAgain, fixed));

        IncidentMemory.Resolution r = memory.resolution(incident);

        assertEquals("Nouvelle version 1.1.3 déployée avec succès", r.description());
        assertEquals(32L, r.minutes());
    }

    @Test
    void goingBackToAnOlderVersionIsNotCalledANewVersion() {
        Deployment incident = deployment(131, "1.1.2-compilation", 3, DeploymentStatus.FAILED, t0, t0.plusMinutes(1));
        Deployment back = deployment(134, "1.1.0", 1, DeploymentStatus.SUCCESS, t0.plusMinutes(20), t0.plusMinutes(21));
        when(guards.findFirstByDeploymentIdOrderByIdDesc(131L)).thenReturn(Optional.empty());
        when(deployments.findTop10ByEnvironmentAndCreatedAtAfterAndDeletedFalseOrderByCreatedAtAsc(eq(testing), any()))
                .thenReturn(List.of(back));

        assertEquals("Retour à la version 1.1.0, plus ancienne", memory.resolution(incident).description());
    }

    @Test
    void anUnresolvedIncidentIsSaidSo() {
        Deployment incident = deployment(140, "2.0.0", 5, DeploymentStatus.FAILED, t0, t0.plusMinutes(1));
        when(guards.findFirstByDeploymentIdOrderByIdDesc(140L)).thenReturn(Optional.empty());
        when(deployments.findTop10ByEnvironmentAndCreatedAtAfterAndDeletedFalseOrderByCreatedAtAsc(eq(testing), any())).thenReturn(List.of());

        IncidentMemory.Resolution r = memory.resolution(incident);

        assertNull(r.minutes());
        assertTrue(r.description().contains("Pas encore résolu"));
    }
}
