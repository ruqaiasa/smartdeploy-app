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
        assertEquals(49, score.score());
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
}
