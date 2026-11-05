package com.smartdeploy.incident;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Banc d'essai hors ligne de l'analyste : une preuve réaliste par scénario
 * du banc de pannes (formats réels de Maven, Docker, TypeScript, Trivy et
 * Kubernetes). Chaque diagnostic doit être exact et citer ses preuves.
 */
class IncidentAnalyzerTest {

    record Case(String name, String expected, IncidentEvidence evidence) {}

    static IncidentEvidence pipeline(String stage, String error, String... log) {
        return new IncidentEvidence(stage, error, List.of(log), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    static List<Case> cases() {
        return List.of(
            new Case("5 compilation", "COMPILATION_ERROR", pipeline("Build Maven", "Échec à l'étape « Build Maven ».",
                "[INFO] Compiling 7 source files with javac [debug release 21] to target/classes",
                "[ERROR] COMPILATION ERROR :",
                "[ERROR] /var/jenkins_home/workspace/smartdeploy/backend/src/main/java/com/spring/render/services/MessageService.java:[42,32] ';' expected",
                "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.13.0:compile (default-compile) on project render: Compilation failure")),
            new Case("6 test unitaire", "TEST_FAILURE", pipeline("Build Maven", "Échec à l'étape « Build Maven ».",
                "[INFO] Running com.spring.render.PanneTest",
                "[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.031 s <<< FAILURE! -- in com.spring.render.PanneTest",
                "[ERROR] com.spring.render.PanneTest.calculDeLaTva -- Time elapsed: 0.012 s <<< FAILURE!",
                "org.opentest4j.AssertionFailedError: Panne simulée : le taux appliqué est erroné ==> expected: <120.0> but was: <119.0>",
                "[ERROR] There are test failures.",
                "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test (default-test) on project render")),
            new Case("7 démarrage impossible", "CONFIG_MISSING", new IncidentEvidence("Verify Kubernetes Rollout",
                "Échec à l'étape « Verify Kubernetes Rollout ». Les pods ne sont pas devenus prêts dans le délai de 900 s. Pods en erreur : fullstack-demo-backend-6d9f (CrashLoopBackOff).",
                List.of("error: deployment \"fullstack-demo-backend\" exceeded its progress deadline"),
                List.of("fullstack-demo-backend-6d9f/fullstack-demo-backend : CrashLoopBackOff ; dernier arrêt : Error (exit code 1) ; 7 redémarrage(s)"),
                List.of("Warning BackOff pod/fullstack-demo-backend-6d9f : Back-off restarting failed container fullstack-demo-backend"),
                List.of("Exception in thread \"main\" java.lang.IllegalStateException: Panne simulée : variable PANNE_CONFIGURATION_OBLIGATOIRE absente, démarrage impossible",
                        "\tat com.spring.render.RenderApplication.main(RenderApplication.java:11)"),
                List.of(), List.of())),
            new Case("8 dockerfile", "DOCKERFILE_INVALID", pipeline("Docker Build", "Échec à l'étape « Docker Build ».",
                "#1 [internal] load build definition from Dockerfile",
                "ERROR: failed to solve: dockerfile parse error on line 13: unknown instruction: COPYY")),
            new Case("9 frontend typescript", "FRONTEND_BUILD", pipeline("Docker Build Frontend", "Échec à l'étape « Docker Build Frontend ».",
                "#12 [build 6/6] RUN npm run build",
                "#12 2.415 src/components/MessageList.tsx(5,9): error TS2322: Type 'string' is not assignable to type 'number'.",
                "ERROR: failed to solve: process \"/bin/sh -c npm run build\" did not complete successfully: exit code: 2")),
            new Case("10 log4shell", "VULNERABILITY", pipeline("Trivy Security Scan", "Échec à l'étape « Trivy Security Scan ».",
                "app/app.jar (jar)",
                "Total: 3 (UNKNOWN: 0, LOW: 0, MEDIUM: 0, HIGH: 1, CRITICAL: 2)",
                "│ org.apache.logging.log4j:log4j-core │ CVE-2021-44228 │ CRITICAL │ fixed │ 2.14.1 │ 2.15.0, 2.3.1 │ log4j-core: Remote code execution in Log4j 2.x │")),
            new Case("1 plantage différé (gardien)", "CRASH_LOOP", new IncidentEvidence(null, null, List.of(),
                List.of("fullstack-demo-backend-7f9c/fullstack-demo-backend : Running ; dernier arrêt : Error (exit code 1) ; 2 redémarrage(s)"),
                List.of("Warning BackOff pod/fullstack-demo-backend-7f9c : Back-off restarting failed container fullstack-demo-backend"),
                List.of("ERROR 1 --- [anne-plantage-differe] c.s.render.faults.PlantageDiffere : Panne simulée : arrêt brutal de l'application (banc d'essai SmartDeploy)"),
                List.of("Redémarrages depuis le déploiement : 2 (référence : moins de 2)"), List.of())),
            new Case("2 fuite mémoire (gardien)", "OUT_OF_MEMORY", new IncidentEvidence(null, null, List.of(),
                List.of("fullstack-demo-backend-7f9c/fullstack-demo-backend : Running ; dernier arrêt : Error (exit code 3) ; 2 redémarrage(s)"),
                List.of("Warning BackOff pod/fullstack-demo-backend-7f9c : Back-off restarting failed container fullstack-demo-backend"),
                List.of("WARN 1 --- [panne-fuite-memoire] c.s.render.faults.FuiteMemoire : Panne simulée : début de la fuite mémoire",
                        "Terminating due to java.lang.OutOfMemoryError: Java heap space"),
                List.of("Redémarrages depuis le déploiement : 2 (référence : moins de 2)", "Mémoire par rapport à avant le déploiement : 1210 Mo (référence : avant : 640 Mo)"), List.of())),
            new Case("réseau (déploiement 103 réel)", "DEPENDENCY_DOWNLOAD", pipeline("Build Maven", "Échec à l'étape « Build Maven ».",
                "[ERROR] Failed to execute goal on project render: Could not resolve dependencies for project com.spring:render:jar:0.0.1-SNAPSHOT",
                "[ERROR] Could not transfer artifact org.postgresql:postgresql:pom:42.7.3 from/to central (https://repo.maven.apache.org/maven2): Connection timed out")),
            new Case("inconnu (garde-fou)", "UNKNOWN", pipeline(null, "Déploiement bloqué : aucune nouvelle de Jenkins depuis 2 heures.",
                "Started by user admin"))
        );
    }

    @Test
    void shouldDiagnoseEveryBenchScenario() {
        for (Case c : cases()) {
            IncidentDiagnosis diagnosis = IncidentAnalyzer.analyze(c.evidence());
            assertEquals(c.expected(), diagnosis.code(), c.name());
            assertFalse(diagnosis.evidence().isEmpty(), "un diagnostic cite toujours ses preuves : " + c.name());
        }
    }

    @Test
    void shouldPreferTheRootCauseOverTheCrashLoopSymptom() {
        IncidentDiagnosis diagnosis = IncidentAnalyzer.analyze(cases().get(2).evidence());
        assertEquals("CONFIG_MISSING", diagnosis.code());
        assertEquals(IncidentDiagnosis.Confidence.HIGH, diagnosis.confidence());
        assertTrue(diagnosis.alternatives().contains("L'application plante en boucle"));
        assertTrue(diagnosis.evidence().stream().anyMatch(line -> line.contains("PANNE_CONFIGURATION_OBLIGATOIRE")));
    }

    @Test
    void shouldNotInventACauseWhenNothingMatches() {
        IncidentDiagnosis diagnosis = IncidentAnalyzer.analyze(cases().get(cases().size() - 1).evidence());
        assertEquals(IncidentDiagnosis.UNKNOWN, diagnosis.code());
        assertEquals(IncidentDiagnosis.Confidence.LOW, diagnosis.confidence());
    }

    /** Cas réel (déploiement #121) : des options de délai banales ne prouvent pas un délai d'analyse. */
    @Test
    void shouldNotMistakeOrdinaryTimeoutOptionsForAScanTimeout() {
        IncidentEvidence evidence = new IncidentEvidence(null, null, List.of(
                "+ curl --fail --silent --show-error --connect-timeout 3 --max-time 10 http://localhost:5000/v2/",
                "> git --version # timeout=10",
                "> git fetch --tags --force --progress -- https://github.com/ruqaiasa/fullstack-app-deploy-render.git # timeout=10"),
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertNotEquals("SCAN_TIMEOUT", IncidentAnalyzer.analyze(evidence).code());
    }

    /** Une signature d'une étape réussie (Trivy non bloquant) ne l'emporte pas sur l'étape en échec. */
    @Test
    void shouldIgnoreSignaturesFromStagesThatSucceeded() {
        IncidentEvidence evidence = new IncidentEvidence("Verify Kubernetes Rollout",
                "Échec à l'étape « Verify Kubernetes Rollout ».",
                List.of("Total: 3 (UNKNOWN: 0, LOW: 0, MEDIUM: 0, HIGH: 1, CRITICAL: 2)"),
                List.of("fullstack-demo-frontend-8498/fullstack-demo-frontend : Running, non prêt ; 0 redémarrage(s)"),
                List.of("Warning Unhealthy pod/fullstack-demo-frontend-8498 : Readiness probe failed: dial tcp 10.42.0.57:80: connect: connection refused"),
                List.of(), List.of(), List.of());

        IncidentDiagnosis diagnosis = IncidentAnalyzer.analyze(evidence);
        assertEquals("PROBE_FAILURE", diagnosis.code());
        assertEquals(IncidentDiagnosis.Confidence.HIGH, diagnosis.confidence());
    }

    @Test
    void shouldDeriveTheKubernetesPrefixFromTheApplicationName() {
        assertEquals("fullstack-demo", IncidentAnalysisService.applicationPrefix(null, "fullstack-demo"));
        assertEquals("fullstack-demo", IncidentAnalysisService.applicationPrefix("fullstack-demo-backend", "autre"));
        assertEquals("mon-app", IncidentAnalysisService.applicationPrefix("", "Mon App"));
    }

    /** Cas réel (déploiement #120) : un message banal de PostgreSQL ne doit pas servir de preuve. */
    @Test
    void shouldNotCiteOrdinaryDatabaseMessagesAsEvidence() {
        IncidentEvidence evidence = new IncidentEvidence("Verify Kubernetes Rollout", "Échec à l'étape « Verify Kubernetes Rollout ».",
                List.of(),
                List.of("postgresql-0/postgresql : Running, prêt ; dernier arrêt : Unknown (exit code 255) ; 1 redémarrage(s)"),
                List.of("Warning BackOff pod/fullstack-demo-backend-75d5 : Back-off restarting failed container fullstack-demo-backend (×6)"),
                List.of("2026-11-03 15:26:50.799 UTC [1] LOG:  background worker \"logical replication launcher\" (PID 33) exited with exit code 1"),
                List.of(), List.of());

        IncidentDiagnosis diagnosis = IncidentAnalyzer.analyze(evidence);
        assertEquals("CRASH_LOOP", diagnosis.code());
        assertTrue(diagnosis.evidence().stream().noneMatch(line -> line.contains("postgresql") || line.contains("replication")));
    }
}
