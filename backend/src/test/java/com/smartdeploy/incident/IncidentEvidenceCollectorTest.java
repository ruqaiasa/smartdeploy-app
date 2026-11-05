package com.smartdeploy.incident;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentEvidenceCollectorTest {

    @Test
    void shouldExtractTheFailedStageFromTheErrorMessage() {
        assertEquals("Build Maven", IncidentEvidenceCollector.failedStage("Échec à l'étape « Build Maven ». La compilation a échoué."));
        assertNull(IncidentEvidenceCollector.failedStage("Déploiement bloqué depuis 2 heures."));
        assertNull(IncidentEvidenceCollector.failedStage(null));
    }

    @Test
    void shouldCleanJenkinsLogAndKeepTheTail() {
        String log = "[Pipeline] stage\n\u001B[1;31m[ERROR] COMPILATION ERROR :\u001B[m\n\nligne 3\nligne 4";
        List<String> lines = IncidentEvidenceCollector.clean(log, 2);
        assertEquals(List.of("ligne 3", "ligne 4"), lines);
        assertEquals("[ERROR] COMPILATION ERROR :", IncidentEvidenceCollector.clean(log, 10).get(0));
    }

    @Test
    void shouldDescribeACrashingContainer() {
        ContainerStatus status = new ContainerStatusBuilder()
                .withName("fullstack-demo-backend").withRestartCount(7).withReady(false)
                .withNewState().withNewWaiting().withReason("CrashLoopBackOff").endWaiting().endState()
                .withNewLastState().withNewTerminated().withReason("Error").withExitCode(1).endTerminated().endLastState()
                .build();

        assertEquals("backend-6d9/fullstack-demo-backend : CrashLoopBackOff ; dernier arrêt : Error (exit code 1) ; 7 redémarrage(s)",
                IncidentEvidenceCollector.describe("backend-6d9", status));
    }

    /** Cas réel (déploiement #120) : un redémarrage de PostgreSQL vieux de deux jours n'est pas une preuve. */
    @Test
    void shouldIgnoreOldRestartsButKeepRecentOnes() {
        Instant now = Instant.parse("2026-11-05T14:50:00Z");
        ContainerStatus oldRestart = new ContainerStatusBuilder()
                .withName("postgresql").withRestartCount(1).withReady(true)
                .withNewState().withNewRunning().endRunning().endState()
                .withNewLastState().withNewTerminated().withReason("Unknown").withExitCode(255)
                .withFinishedAt("2026-11-03T15:26:00Z").endTerminated().endLastState()
                .build();
        ContainerStatus recentCrash = new ContainerStatusBuilder(oldRestart)
                .withName("fullstack-demo-backend")
                .withNewLastState().withNewTerminated().withReason("Error").withExitCode(1)
                .withFinishedAt("2026-11-05T14:45:00Z").endTerminated().endLastState()
                .build();
        ContainerStatus blocked = new ContainerStatusBuilder()
                .withName("fullstack-demo-backend").withRestartCount(0).withReady(false)
                .withNewState().withNewWaiting().withReason("CrashLoopBackOff").endWaiting().endState()
                .build();

        assertFalse(IncidentEvidenceCollector.isTroubled(oldRestart, now));
        assertTrue(IncidentEvidenceCollector.isTroubled(recentCrash, now));
        assertTrue(IncidentEvidenceCollector.isTroubled(blocked, now));
    }
}
