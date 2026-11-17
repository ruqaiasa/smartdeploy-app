package com.smartdeploy.incident;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Les faits précis et les actions concrètes, sur les formats réels des pannes du banc d'essai. */
class IncidentFactsTest {

    private static IncidentEvidence pipeline(String stage, String... log) {
        return new IncidentEvidence(stage, null, List.of(log), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static IncidentEvidence runtime(List<String> pods, List<String> events, List<String> logs) {
        return new IncidentEvidence(null, null, List.of(), pods, events, logs, List.of(), List.of());
    }

    private static String fact(IncidentFacts.Enrichment e, String label) {
        return e.facts().stream().filter(f -> f.label().equals(label)).map(IncidentFacts.Fact::value).findFirst().orElse(null);
    }

    @Test
    void compilationErrorGivesFileLineAndMessage() {
        var e = IncidentFacts.enrich("COMPILATION_ERROR", pipeline("Build Maven",
                "[INFO] Compiling 6 source files",
                "[ERROR] /var/lib/jenkins/workspace/smartdeploy/backend/src/main/java/com/spring/render/services/MessageService.java:[47,32] ';' expected"));
        assertEquals("MessageService.java", fact(e, "Fichier"));
        assertEquals("47", fact(e, "Ligne"));
        assertTrue(e.action().startsWith("Corriger MessageService.java, ligne 47"));
    }

    @Test
    void failingTestIsNamed() {
        var e = IncidentFacts.enrich("TEST_FAILURE", pipeline("Build Maven",
                "[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0",
                "[ERROR]   PanneTest.prixTtc:18 Panne simulée : le taux appliqué est erroné ==> expected: <120.0> but was: <119.0>"));
        assertEquals("PanneTest.prixTtc", fact(e, "Test en échec"));
        assertTrue(e.action().contains("PanneTest.prixTtc échoue (ligne 18)"));
    }

    @Test
    void typescriptAndDockerfileErrorsArePrecise() {
        var ts = IncidentFacts.enrich("FRONTEND_BUILD", pipeline("Docker Build Frontend",
                "src/views/HomeView.vue(12,9): error TS2322: Type 'string' is not assignable to type 'number'."));
        assertEquals("src/views/HomeView.vue", fact(ts, "Fichier"));
        var docker = IncidentFacts.enrich("DOCKERFILE_INVALID", pipeline("Docker Build",
                "#7 ERROR: dockerfile parse error on line 14: unknown instruction: COPYY"));
        assertEquals("COPYY", fact(docker, "Instruction inconnue"));
        assertEquals("14", fact(docker, "Ligne du Dockerfile"));
    }

    @Test
    void vulnerabilityGivesLibraryAndFixedVersion() {
        // Tableau réel de Trivy : la gravité est fusionnée sur les lignes suivantes d'un même groupe.
        var e = IncidentFacts.enrich("VULNERABILITY", pipeline("Trivy Security Scan",
                "│ libcrypto3 │ CVE-2026-31789 │ CRITICAL │ fixed  │ 3.3.3-r0          │ 3.3.7-r0      │ openssl: OpenSSL",
                "│ org.springframework:spring-webmvc (app.jar) │ CVE-2026-47884 │          │        │ 6.1.12 │ 7.0.9 │ Spring"));
        assertEquals("libcrypto3 3.3.3-r0 → 3.3.7-r0", fact(e, "CVE-2026-31789 (CRITICAL)"));
        assertEquals("org.springframework:spring-webmvc (app.jar) 6.1.12 → 7.0.9", fact(e, "CVE-2026-47884 (CRITICAL)"));
        assertTrue(e.action().contains("spring-webmvc (app.jar) 6.1.12 → 7.0.9"));
    }

    @Test
    void missingConfigurationIsNamed() {
        var e = IncidentFacts.enrich("CONFIG_MISSING", runtime(List.of(), List.of(), List.of(
                "Caused by: java.lang.IllegalStateException: Panne simulée : variable PANNE_CONFIGURATION_OBLIGATOIRE absente, démarrage impossible")));
        assertEquals("PANNE_CONFIGURATION_OBLIGATOIRE", fact(e, "Variable d'environnement manquante"));
        assertTrue(e.action().contains("« PANNE_CONFIGURATION_OBLIGATOIRE »"));
    }

    @Test
    void probeFailureComparesProbedAndListeningPorts() {
        // Cas réel #119 : la sonde frappe 9090, Nginx non privilégié écoute sur 8080.
        String event = "Warning Unhealthy pod/x : Startup probe failed: dial tcp 10.42.0.44:9090: connect: connection refused (×121)";
        var unknownPort = IncidentFacts.enrich("PROBE_FAILURE", runtime(List.of(), List.of(event), List.of()));
        assertEquals("9090", fact(unknownPort, "Port sondé par Kubernetes"));
        var known = IncidentFacts.enrich("PROBE_FAILURE", runtime(List.of(), List.of(event),
                List.of("Tomcat started on port 8080 (http) with context path '/'")));
        assertEquals("8080", fact(known, "Port réellement écouté"));
        assertTrue(known.action().contains("redéployer avec le port 8080"));
    }

    @Test
    void failedDownloadPointsToTheServerAndSuggestsRetry() {
        // Cas réel #126 : échec passager du dépôt Maven pendant la construction de l'image.
        var e = IncidentFacts.enrich("DEPENDENCY_DOWNLOAD", pipeline("Docker Build",
                "#13 5.977 wget: Failed to fetch https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"));
        assertEquals("repo.maven.apache.org", fact(e, "Serveur"));
        assertTrue(e.action().contains("relancer le déploiement"));
        assertEquals("DEPENDENCY_DOWNLOAD", IncidentAnalyzer.analyze(pipeline("Docker Build",
                "#13 5.977 wget: Failed to fetch https://repo.maven.apache.org/maven2/x.zip")).code(), "plus « cause non identifiée »");
    }

    @Test
    void crashLoopExplainsExitCodeTimingAndDecisiveLine() {
        // Cas réel #129 : arrêt brutal programmé 90 s après le démarrage.
        var e = IncidentFacts.enrich("CRASH_LOOP", runtime(
                List.of("fullstack-demo-backend-85cf/fullstack-demo-backend : Running, non prêt ; dernier arrêt : Error (exit code 1) ; durée de fonctionnement : 92 s ; 3 redémarrage(s)"),
                List.of(),
                List.of("INFO Started RenderApplication in 6.2 seconds",
                        "ERROR c.s.r.PlantageDiffere : Panne simulée : arrêt brutal de l'application (banc d'essai SmartDeploy)")));
        assertEquals("1 : erreur de l'application", fact(e, "Code de sortie"));
        assertTrue(fact(e, "Moment de l'arrêt").contains("en cours d'exécution, pas au démarrage"));
        assertTrue(e.decisiveLine().contains("Panne simulée : arrêt brutal"));
        assertTrue(e.action().contains("ligne décisive"));
    }

    @Test
    void killedContainerIsDiagnosedAsMemory() {
        var e = IncidentFacts.enrich("CRASH_LOOP", runtime(
                List.of("backend/app : CrashLoopBackOff ; dernier arrêt : OOMKilled (exit code 137) ; durée de fonctionnement : 240 s ; 4 redémarrage(s)"),
                List.of(), List.of()));
        assertTrue(fact(e, "Code de sortie").startsWith("137 : tué par le système"));
        assertTrue(e.action().startsWith("Le conteneur a épuisé sa mémoire"));
    }

    @Test
    void buildFailureIgnoresRuntimeEvidenceOfAnotherIncident() {
        // Cas réel #131 : compilation en échec, alors que les événements de la panne #129 sont encore visibles.
        IncidentEvidence evidence = new IncidentEvidence("Build Maven", null,
                List.of("[ERROR] /var/lib/jenkins/workspace/smartdeploy/backend/src/main/java/com/spring/render/services/MessageService.java:[38,31] ';' expected"),
                List.of("fullstack-demo-backend-85cf/fullstack-demo-backend : Running, prêt ; dernier arrêt : Error (exit code 1) ; 3 redémarrage(s)"),
                List.of("Warning BackOff pod/fullstack-demo-backend-85cf : Back-off restarting failed container (×2)"),
                List.of(), List.of(), List.of());
        IncidentDiagnosis diagnosis = IncidentAnalyzer.analyze(evidence);
        assertEquals("COMPILATION_ERROR", diagnosis.code());
        assertTrue(diagnosis.alternatives().isEmpty(), "aucune cause d'exécution : rien n'a été déployé");
    }
}
