package com.smartdeploy.guard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuardEvaluatorTest {

    private static GuardSnapshot healthy() {
        return new GuardSnapshot(3, 0, 0, List.of(), List.of(), 0.42, 12.0, 410.0, 10.0, 400.0);
    }

    @Test
    void shouldJudgeHealthyApplicationOk() {
        GuardEvaluation evaluation = GuardEvaluator.evaluate(healthy());
        assertEquals(GuardLevel.OK, evaluation.level());
        assertTrue(evaluation.critical().isEmpty());
        assertEquals(7, evaluation.checks().size());
    }

    @Test
    void shouldDetectCrashLoopWithEvidence() {
        GuardEvaluation evaluation = GuardEvaluator.evaluate(new GuardSnapshot(3, 1, 4,
                List.of("fullstack-demo-backend-7f9 : CrashLoopBackOff"), List.of(), 0.3, 5.0, 300.0, 10.0, 400.0));

        assertEquals(GuardLevel.CRITICAL, evaluation.level());
        assertEquals(List.of("availability", "blocked", "restarts"),
                evaluation.critical().stream().map(GuardCheck::key).toList());
        assertTrue(evaluation.critical().get(1).observed().contains("CrashLoopBackOff"));
    }

    @Test
    void shouldDetectOutOfMemoryOnlyWhenTheContainerRestarted() {
        GuardSnapshot oom = new GuardSnapshot(3, 0, 1, List.of(), List.of("backend-7f9"), 0.96, 15.0, 980.0, 10.0, 400.0);
        assertTrue(GuardEvaluator.evaluate(oom).critical().stream().anyMatch(c -> c.key().equals("oom")));

        GuardSnapshot oldOom = new GuardSnapshot(3, 0, 0, List.of(), List.of("backend-7f9"), 0.4, 15.0, 410.0, 10.0, 400.0);
        assertTrue(GuardEvaluator.evaluate(oldOom).critical().isEmpty(), "un ancien OOMKilled sans redémarrage récent n'est pas critique");
    }

    @Test
    void shouldOnlyWarnOnResourceDrift() {
        GuardEvaluation cpu = GuardEvaluator.evaluate(new GuardSnapshot(3, 0, 0, List.of(), List.of(), 0.5, 95.0, 420.0, 10.0, 400.0));
        assertEquals(GuardLevel.WARNING, cpu.level());
        assertEquals("cpu_drift", cpu.warnings().get(0).key());

        GuardEvaluation smallIncrease = GuardEvaluator.evaluate(new GuardSnapshot(3, 0, 0, List.of(), List.of(), 0.5, 18.0, 420.0, 8.0, 400.0));
        assertEquals(GuardLevel.OK, smallIncrease.level(), "un doublement de quelques points n'est pas une dérive");
    }

    @Test
    void shouldWarnOnSingleRestartAndBeCriticalFromTwo() {
        assertEquals(GuardLevel.WARNING, GuardEvaluator.evaluate(new GuardSnapshot(3, 0, 1, List.of(), List.of(), 0.4, 12.0, 410.0, 10.0, 400.0)).level());
        assertEquals(GuardLevel.CRITICAL, GuardEvaluator.evaluate(new GuardSnapshot(3, 0, 2, List.of(), List.of(), 0.4, 12.0, 410.0, 10.0, 400.0)).level());
    }

    @Test
    void shouldNotInventABaselineForAFirstDeployment() {
        GuardEvaluation evaluation = GuardEvaluator.evaluate(new GuardSnapshot(3, 0, 0, List.of(), List.of(), 0.4, 12.0, 410.0, null, null));
        assertEquals(GuardLevel.OK, evaluation.level());
        assertEquals(GuardLevel.UNKNOWN, evaluation.checks().stream().filter(c -> c.key().equals("cpu_drift")).findFirst().orElseThrow().level());
    }

    @Test
    void shouldReportUnknownWhenPrometheusIsUnreachable() {
        assertEquals(GuardLevel.UNKNOWN, GuardEvaluator.evaluate(null).level());
    }

    @Test
    void shouldNotJudgeResourceDriftDuringWarmUp() {
        GuardSnapshot startup = new GuardSnapshot(4, 0, 0, List.of(), List.of(), null, 39.0, 993.0, 11.0, 811.0);

        GuardEvaluation early = GuardEvaluator.evaluate(startup, 7);
        assertEquals(GuardLevel.OK, early.level(), "le démarrage de la JVM n'est pas une anomalie");
        assertEquals(GuardLevel.UNKNOWN, early.checks().stream().filter(c -> c.key().equals("cpu_drift")).findFirst().orElseThrow().level());

        assertEquals(GuardLevel.WARNING, GuardEvaluator.evaluate(startup, 240).level(), "après 3 minutes, la dérive est jugée");
    }

    @Test
    void shouldKeepCriticalChecksActiveDuringWarmUp() {
        GuardSnapshot crash = new GuardSnapshot(4, 1, 2, List.of("backend-7f9 : CrashLoopBackOff"), List.of(), null, 5.0, 300.0, 11.0, 811.0);
        assertEquals(GuardLevel.CRITICAL, GuardEvaluator.evaluate(crash, 30).level());
    }
}
