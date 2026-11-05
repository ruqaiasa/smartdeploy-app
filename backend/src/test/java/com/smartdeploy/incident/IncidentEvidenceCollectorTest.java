package com.smartdeploy.incident;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import org.junit.jupiter.api.Test;

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
}
