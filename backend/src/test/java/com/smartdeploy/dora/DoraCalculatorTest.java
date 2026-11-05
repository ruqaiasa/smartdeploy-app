package com.smartdeploy.dora;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Historique proche des essais réels des 4 et 5 novembre 2026. */
class DoraCalculatorTest {

    private static LocalDateTime t(int day, int h, int m, int s) {
        return LocalDateTime.of(2026, 11, day, h, m, s);
    }

    private static final List<DeploymentFact> FACTS = List.of(
            new DeploymentFact(114L, 5L, "SUCCESS", false, false, t(4, 21, 18, 0), t(4, 21, 31, 0)),
            new DeploymentFact(116L, 5L, "SUCCESS", true, false, t(4, 22, 50, 30), t(4, 22, 51, 5)),
            new DeploymentFact(117L, 5L, "SUCCESS", false, true, t(4, 23, 5, 20), t(4, 23, 6, 48)),
            new DeploymentFact(118L, 5L, "SUCCESS", true, false, t(4, 23, 10, 0), t(4, 23, 10, 35)),
            new DeploymentFact(119L, 5L, "FAILED", false, false, t(5, 9, 0, 0), t(5, 9, 16, 0)),
            new DeploymentFact(120L, 5L, "FAILED", false, false, t(5, 9, 30, 0), t(5, 9, 46, 0)),
            new DeploymentFact(122L, 5L, "SUCCESS", false, false, t(5, 14, 58, 24), t(5, 14, 59, 46)),
            new DeploymentFact(123L, 5L, "OTHER", false, false, t(5, 15, 30, 0), null));

    private static Map<String, DoraMetric> metrics(List<DeploymentFact> facts) {
        return DoraCalculator.compute(facts, 7).stream().collect(Collectors.toMap(DoraMetric::key, m -> m));
    }

    @Test
    void shouldCountOnlyChangesNotRollbacks() {
        assertEquals(3.0, metrics(FACTS).get("deployment_frequency").value());
    }

    @Test
    void shouldCountPipelineFailuresAndDegradationsAsFailedChanges() {
        DoraMetric rate = metrics(FACTS).get("change_failure_rate");
        assertEquals(60.0, rate.value(), "3 changements en échec sur 5 terminés");
        assertEquals("LOW", rate.level());
    }

    @Test
    void shouldMeasureRestorationUntilTheNextHealthyDeployment() {
        DoraMetric restore = metrics(FACTS).get("time_to_restore");
        assertEquals(3.8, restore.value(), "de 23:06:48 (mise en service dégradée) à 23:10:35 (rollback réussi)");
        assertEquals("ELITE", restore.level());
    }

    @Test
    void shouldReportNoLevelWithoutData() {
        Map<String, DoraMetric> empty = metrics(List.of());
        assertEquals("NONE", empty.get("lead_time").level());
        assertNull(empty.get("time_to_restore").value());
    }

    @Test
    void shouldComputeTheMedian() {
        assertEquals(2.0, DoraCalculator.median(List.of(3.0, 1.0, 2.0)));
        assertEquals(2.5, DoraCalculator.median(List.of(1.0, 2.0, 3.0, 4.0)));
    }
}
