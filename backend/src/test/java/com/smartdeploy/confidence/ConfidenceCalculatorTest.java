package com.smartdeploy.confidence;

import com.smartdeploy.environment.EnvironmentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfidenceCalculatorTest {

    @Test
    void shouldTrustAHealthyVersionOnTheMainBranch() {
        ConfidenceScore score = ConfidenceCalculator.compute(
                new ConfidenceInputs(10, 0, 0, false, 0, true, EnvironmentType.DEVELOPMENT, "master"));
        assertEquals(100, score.score());
        assertEquals("HIGH", score.level());
        assertTrue(score.factors().isEmpty());
    }

    @Test
    void shouldWarnStronglyAboutAVersionAlreadyJudgedDegraded() {
        ConfidenceScore score = ConfidenceCalculator.compute(
                new ConfidenceInputs(10, 2, 0, true, 1, false, EnvironmentType.DEVELOPMENT, "panne/plantage-differe"));
        // 100 − 50 (dégradée) − 6 (2 échecs sur 10) − 10 (incident) − 5 (branche de travail)
        assertEquals(29, score.score());
        assertEquals("LOW", score.level());
        assertEquals("Version déjà jugée dégradée", score.factors().get(0).label());
    }

    @Test
    void shouldAskForCautionInProductionWithoutValidation() {
        ConfidenceScore score = ConfidenceCalculator.compute(
                new ConfidenceInputs(4, 0, 0, false, 0, false, EnvironmentType.PRODUCTION, "master"));
        assertEquals(75, score.score());
        assertEquals("MEDIUM", score.level());
    }

    @Test
    void shouldJustifyEveryPointRemovedAndNeverGoBelowZero() {
        ConfidenceScore score = ConfidenceCalculator.compute(
                new ConfidenceInputs(10, 10, 4, true, 3, false, EnvironmentType.PRODUCTION, "feature/x"));
        assertEquals(0, score.score());
        assertTrue(score.factors().stream().allMatch(f -> f.points() < 0 && !f.explanation().isBlank()));
    }

    @Test
    void shouldNotPunishOneExternalFailureThreeTimes() {
        // Cas réel du test de bout en bout : un seul échec passager (réseau) sur un environnement neuf.
        // Avant : −25 (version) −30 (environnement, 1 sur 1) −10 (incident), pour le même échec.
        ConfidenceScore score = ConfidenceCalculator.compute(
                new ConfidenceInputs(1, 1, 0, false, 0, true, EnvironmentType.TEST, "master"));
        assertEquals(100, score.score(), "un seul déploiement terminé : taux d'échec non significatif");
        assertTrue(score.factors().isEmpty());
    }

    @Test
    void shouldOnlyBlameTheVersionForCodeCauses() {
        assertTrue(ConfidenceCalculator.isVersionCause("COMPILATION_ERROR"));
        assertTrue(ConfidenceCalculator.isVersionCause("CRASH_LOOP"));
        assertFalse(ConfidenceCalculator.isVersionCause("REGISTRY_UNAVAILABLE"), "panne extérieure");
        assertFalse(ConfidenceCalculator.isVersionCause("DEPENDENCY_DOWNLOAD"), "téléchargement impossible");
        assertFalse(ConfidenceCalculator.isVersionCause(null), "cause non identifiée");
    }

    @Test
    void aVersionThatAlreadyCrashedTwiceIsAHighRisk() {
        // Cas réel : 1.1.1-plantage, deux mises en service dégradées et retirées par le gardien.
        // Avant la correction : 100 − 30 − 5 = 65, « Prudence ».
        ConfidenceScore score = ConfidenceCalculator.compute(
                new ConfidenceInputs(10, 0, 0, true, 0, false, EnvironmentType.DEVELOPMENT, "panne/plantage-differe", 2));
        assertEquals(30, score.score(), "100 − 50 − 15 − 5");
        assertEquals("LOW", score.level());
        assertTrue(score.factors().get(0).explanation().contains("2 dégradations"));
    }
}
