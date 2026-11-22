package com.smartdeploy.portal;

import com.smartdeploy.deployment.DeploymentStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Fiche de déploiement : étape réelle, phases, masquage des secrets, adresse de l'application. */
class DeploymentProgressTest {

    static final String CONSOLE = String.join("\n",
            "[Pipeline] { (Notify RUNNING)", "[Pipeline] }",
            "[Pipeline] { (Checkout Git)", "[Pipeline] }",
            "[Pipeline] { (Build Maven)", "[ERROR] MessageService.java:[38,9] ';' expected");

    @Test
    void theCurrentStageIsTheLastOneStartedInTheConsole() {
        int index = DeploymentProgressController.lastStageIndex(CONSOLE);
        assertEquals("Build Maven", DeploymentProgressController.STAGES.get(index));
        assertEquals(-1, DeploymentProgressController.lastStageIndex(null));
    }

    @Test
    void aFailureMarksItsPhaseAndLeavesTheNextOnesPending() {
        int buildMaven = DeploymentProgressController.STAGES.indexOf("Build Maven");
        List<DeploymentProgressController.PhaseState> phases =
                DeploymentProgressController.phaseStates(buildMaven, DeploymentStatus.FAILED);
        assertEquals("DONE", phases.get(0).status());
        assertEquals("FAILED", phases.get(1).status(), "Construction et qualité");
        assertEquals("PENDING", phases.get(4).status());
        assertTrue(DeploymentProgressController.phaseStates(31, DeploymentStatus.SUCCESS).stream().allMatch(p -> p.status().equals("DONE")));
    }

    @Test
    void passwordsAndTokensAreMaskedInLogs() {
        List<String> lines = DeploymentProgressController.cleanLines(
                "spring.datasource.password=1234\nINFO started\napi_key: abcdef\nERROR Connection refused", false);
        assertEquals("spring.datasource.password=••••", lines.get(0));
        assertFalse(lines.get(2).contains("abcdef"));
        assertEquals(List.of("ERROR Connection refused"),
                DeploymentProgressController.cleanLines("INFO started\nERROR Connection refused", true));
    }

    @Test
    void theApplicationAddressComesFromTheIngressHost() {
        assertEquals("http://demo.local", DeploymentProgressController.hostOf("{\"ingress\":{\"host\":\"demo.local\"}}"));
        assertNull(DeploymentProgressController.hostOf("{\"namespace\":\"sd-dev\"}"));
        assertNull(DeploymentProgressController.hostOf("pas du json"));
    }
}
