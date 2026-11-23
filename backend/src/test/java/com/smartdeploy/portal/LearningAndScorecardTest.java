package com.smartdeploy.portal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Analyste apprenant et fiche d'excellence : les calculs. */
class LearningAndScorecardTest {

    @Test
    void aCauseOftenConfirmedGainsConfidence() {
        AnalystLearning.Learning l = AnalystLearning.of("COMPILATION", "MEDIUM", 8, 0, 1);
        assertEquals("HIGH", l.calibratedConfidence(), "(0,6 x 4 + 8) / 13 = 0,80");
        assertEquals(89, l.precision());
        assertTrue(l.note().contains("8 fois sur 9"));
        assertTrue(l.note().contains("relevée"));
    }

    @Test
    void aCauseOftenRefutedLosesConfidence() {
        AnalystLearning.Learning l = AnalystLearning.of("CRASH_LOOP", "HIGH", 0, 1, 4);
        assertEquals("LOW", l.calibratedConfidence(), "(0,85 x 4 + 0,5) / 9 = 0,43");
        assertTrue(l.note().contains("abaissée"));
    }

    @Test
    void withoutReviewsTheInitialConfidenceIsKept() {
        AnalystLearning.Learning l = AnalystLearning.of("DOCKERFILE", "HIGH", 0, 0, 0);
        assertEquals("HIGH", l.calibratedConfidence());
        assertNull(l.precision());
    }

    @Test
    void theScoreIgnoresUnknownCriteriaAndRanksRecommendations() {
        ScorecardCalculator.Result r = ScorecardCalculator.compute(List.of(
                new ScorecardCalculator.Criterion("QUALITY", "Tests", true, 40, "", "a"),
                new ScorecardCalculator.Criterion("QUALITY", "Java", false, 15, "", "Passer à Java 17"),
                new ScorecardCalculator.Criterion("SECURITY", "Secrets", false, 40, "", "Retirer les mots de passe"),
                new ScorecardCalculator.Criterion("SECURITY", "Trivy", true, 40, "", "b"),
                new ScorecardCalculator.Criterion("SPEED", "Durée", null, 50, "", "c")));
        assertEquals(73, r.axes().get(0).score(), "40 / 55");
        assertEquals(50, r.axes().get(1).score());
        assertNull(r.axes().get(3).score(), "rapidité inconnue : non comptée");
        assertEquals(62, r.score(), "(73 + 50) / 2");
        assertEquals("C", r.grade());
        assertEquals("Retirer les mots de passe", r.recommendations().get(0).recommendation(), "40 points avant 15");
    }

    @Test
    void gradesFollowTheThresholds() {
        assertEquals("A", ScorecardCalculator.grade(90));
        assertEquals("B", ScorecardCalculator.grade(70));
        assertEquals("E", ScorecardCalculator.grade(10));
        assertEquals("?", ScorecardCalculator.grade(null));
    }
}
