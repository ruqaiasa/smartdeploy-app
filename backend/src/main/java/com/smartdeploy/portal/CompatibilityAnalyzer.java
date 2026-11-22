package com.smartdeploy.portal;

import com.smartdeploy.portal.CompatibilityReport.Check;
import com.smartdeploy.portal.CompatibilityReport.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vérifie qu'un dépôt peut être déployé par SmartDeploy, à partir de sa structure et de quelques fichiers.
 * Les règles reprennent exactement ce qu'exige le pipeline : un projet Maven à la racine ou dans backend/,
 * le script mvnw, un Dockerfile, un port, et, pour une application complète, une interface dans frontend/.
 * Calcul pur, sans réseau : testable sur un vrai dépôt.
 */
public final class CompatibilityAnalyzer {

    private static final Pattern PARENT_VERSION = Pattern.compile(
            "<artifactId>spring-boot-starter-parent</artifactId>\\s*<version>([^<]+)</version>");
    private static final Pattern JAVA_VERSION = Pattern.compile("<java\\.version>(\\d+)</java\\.version>");
    private static final Pattern EXPOSE = Pattern.compile("(?m)^\\s*EXPOSE\\s+(\\d+)");
    private static final Pattern SERVER_PORT = Pattern.compile("(?m)^\\s*server\\.port\\s*[=:]\\s*\\$?\\{?[^:}]*?:?(\\d+)");
    private static final Pattern CLEAR_PASSWORD = Pattern.compile(
            "(?m)^\\s*[\\w.-]*password\\s*[=:]\\s*(?!\\$\\{)(?![\\s]*$)(\\S+)");

    private CompatibilityAnalyzer() {
    }

    public static CompatibilityReport analyze(RepositorySnapshot repo) {
        List<Check> checks = new ArrayList<>();
        String backend = repo.has("pom.xml") ? "" : repo.has("backend/pom.xml") ? "backend/" : null;

        // 1. Projet du serveur (Maven)
        if (backend == null) {
            boolean gradle = repo.has("build.gradle") || repo.has("backend/build.gradle") || repo.has("build.gradle.kts");
            checks.add(new Check("projet", "Projet du serveur", Status.ERROR,
                    gradle ? "Projet Gradle trouvé : le pipeline de SmartDeploy compile avec Maven."
                            : "Aucun fichier pom.xml à la racine ni dans backend/.",
                    "Placez un projet Maven (pom.xml) à la racine du dépôt ou dans un dossier backend/."));
        } else {
            checks.add(new Check("projet", "Projet du serveur", Status.OK,
                    "Projet Maven trouvé " + (backend.isEmpty() ? "à la racine du dépôt." : "dans backend/."), null));
        }

        if (backend != null) {
            String pom = repo.file(backend + "pom.xml");
            // 2. Script mvnw, exigé par le pipeline
            checks.add(repo.has(backend + "mvnw")
                    ? new Check("mvnw", "Script Maven Wrapper (mvnw)", Status.OK, "Présent : la compilation n'a besoin d'aucune installation.", null)
                    : new Check("mvnw", "Script Maven Wrapper (mvnw)", Status.ERROR, "Absent : le pipeline compile avec ./mvnw.",
                            "Dans le dossier du projet, lancez « mvn wrapper:wrapper », puis versionnez mvnw et le dossier .mvn."));
            // 3. Spring Boot et sa version
            Matcher boot = PARENT_VERSION.matcher(pom);
            if (boot.find()) {
                String v = boot.group(1).trim();
                int major = leadingNumber(v);
                checks.add(major >= 3
                        ? new Check("spring-boot", "Spring Boot", Status.OK, "Version " + v + ".", null)
                        : new Check("spring-boot", "Spring Boot", Status.WARNING, "Version " + v + " : cette génération n'est plus maintenue.",
                                "Passez à Spring Boot 3 ou plus récent : le contrôle de sécurité risque de bloquer des bibliothèques vulnérables."));
            } else {
                checks.add(new Check("spring-boot", "Spring Boot", Status.INFO,
                        "Projet Maven sans parent Spring Boot : le déploiement reste possible si l'image démarre seule.", null));
            }
            // 4. Version de Java
            Matcher java = JAVA_VERSION.matcher(pom);
            if (java.find()) {
                int jv = Integer.parseInt(java.group(1));
                checks.add(jv >= 17
                        ? new Check("java", "Version de Java", Status.OK, "Java " + jv + ".", null)
                        : new Check("java", "Version de Java", Status.WARNING, "Java " + jv + " : version ancienne.",
                                "Passez à Java 17 ou plus récent (requis par Spring Boot 3)."));
            }
            // 5. Dockerfile du serveur
            String dockerfile = backend + "Dockerfile";
            if (repo.has(dockerfile)) {
                checks.add(new Check("dockerfile", "Dockerfile du serveur", Status.OK, "Trouvé : " + dockerfile + ".", null));
            } else {
                checks.add(new Check("dockerfile", "Dockerfile du serveur", Status.ERROR, "Aucun Dockerfile dans " + (backend.isEmpty() ? "la racine" : backend) + ".",
                        "Ajoutez un Dockerfile en deux étapes : construction avec Maven, puis exécution avec une image JRE légère (par exemple eclipse-temurin:21-jre-alpine)."));
            }
            // 6. Port
            String config = repo.file(backend + "src/main/resources/application.properties") + "\n"
                    + repo.file(backend + "src/main/resources/application.yml") + "\n"
                    + repo.file(backend + "src/main/resources/application.yaml");
            Integer port = firstInt(EXPOSE, repo.file(dockerfile));
            if (port == null) port = firstInt(SERVER_PORT, config);
            if (port == null) {
                checks.add(new Check("port", "Port de l'application", Status.INFO, "Aucun port déclaré : le port 8080, par défaut de Spring Boot, sera utilisé.", null));
            } else if (port == 8080) {
                checks.add(new Check("port", "Port de l'application", Status.OK, "Port 8080.", null));
            } else {
                checks.add(new Check("port", "Port de l'application", Status.WARNING, "Port " + port + " : SmartDeploy propose 8080 par défaut.",
                        "Indiquez le port " + port + " dans les options avancées de l'assistant de déploiement."));
            }
            // 7. Tests automatisés
            boolean tests = repo.paths().stream().anyMatch(p -> p.startsWith(backend + "src/test/") && p.endsWith(".java"));
            checks.add(tests
                    ? new Check("tests", "Tests automatisés", Status.OK, "Des tests sont présents : ils seront exécutés par le pipeline.", null)
                    : new Check("tests", "Tests automatisés", Status.WARNING, "Aucun test trouvé dans src/test.",
                            "Ajoutez au moins des tests unitaires : le seuil de qualité SonarQube tient compte de la couverture."));
            // 8. Mots de passe écrits en clair
            Matcher clear = CLEAR_PASSWORD.matcher(config);
            if (clear.find()) {
                checks.add(new Check("secrets", "Mots de passe dans le dépôt", Status.WARNING,
                        "Un mot de passe est écrit en clair dans la configuration de l'application.",
                        "Remplacez-le par une variable d'environnement (par exemple ${SPRING_DATASOURCE_PASSWORD}) : SmartDeploy fournit le mot de passe par un Secret Kubernetes."));
            } else {
                checks.add(new Check("secrets", "Mots de passe dans le dépôt", Status.OK, "Aucun mot de passe écrit en clair détecté.", null));
            }
            // 9. Métriques applicatives
            boolean metrics = pom.contains("micrometer-registry-prometheus");
            checks.add(metrics
                    ? new Check("metriques", "Métriques de l'application", Status.OK, "Micrometer et Prometheus : l'application expose ses propres métriques.", null)
                    : new Check("metriques", "Métriques de l'application", Status.INFO,
                            "Pas de métriques applicatives : la supervision se limitera aux métriques du cluster (processeur, mémoire, redémarrages).", null));
        }

        // 10. Interface
        String frontend = repo.has("frontend/package.json") ? "frontend/"
                : (backend != null && !backend.isEmpty() && repo.has("package.json")) ? "" : null;
        if (frontend != null) {
            checks.add(repo.has(frontend + "Dockerfile")
                    ? new Check("interface", "Interface", Status.OK, "Interface trouvée " + (frontend.isEmpty() ? "à la racine" : "dans frontend/") + ", avec son Dockerfile.", null)
                    : new Check("interface", "Interface", Status.WARNING, "Interface trouvée, mais sans Dockerfile.",
                            "Ajoutez un Dockerfile : construction avec Node, puis service des fichiers avec Nginx."));
        }

        // 11. Base de données et architecture conseillée
        String pom = backend == null ? "" : repo.file(backend + "pom.xml");
        boolean postgres = pom.contains("<artifactId>postgresql</artifactId>");
        boolean otherDb = pom.contains("mysql-connector") || pom.contains("mariadb-java-client") || pom.contains("mongodb");
        if (postgres) {
            checks.add(new Check("base", "Base de données", Status.OK, "PostgreSQL détecté : SmartDeploy peut la déployer et la sauvegarder.", null));
        } else if (otherDb) {
            checks.add(new Check("base", "Base de données", Status.INFO,
                    "Base de données autre que PostgreSQL : elle restera externe, SmartDeploy ne gère que PostgreSQL.", null));
        }
        String architecture = backend == null ? null
                : frontend != null && repo.has(frontend + "Dockerfile") ? (postgres ? "FULLSTACK_MANAGED" : "FULLSTACK_EXTERNAL")
                : "BACKEND";
        String architectureLabel = architecture == null ? null : switch (architecture) {
            case "FULLSTACK_MANAGED" -> "Frontend, backend et base (PostgreSQL déployée dans le cluster)";
            case "FULLSTACK_EXTERNAL" -> "Frontend et backend, avec une base externe";
            default -> "Backend seul";
        };

        long errors = checks.stream().filter(c -> c.status() == Status.ERROR).count();
        long warnings = checks.stream().filter(c -> c.status() == Status.WARNING).count();
        String verdict = errors > 0 ? "NOT_READY" : warnings > 0 ? "ALMOST_READY" : "READY";
        String verdictLabel = errors > 0 ? "À adapter : " + errors + " point" + (errors > 1 ? "s" : "") + " bloquant" + (errors > 1 ? "s" : "")
                : warnings > 0 ? "Presque prêt : " + warnings + " point" + (warnings > 1 ? "s" : "") + " à améliorer"
                : "Prêt à être déployé";
        return new CompatibilityReport(repo.repository(), repo.branch(), verdict, verdictLabel, architecture, architectureLabel,
                backend == null ? null : (backend.isEmpty() ? "." : "backend"), frontend == null ? null : (frontend.isEmpty() ? "." : "frontend"), checks);
    }

    private static int leadingNumber(String version) {
        Matcher m = Pattern.compile("^(\\d+)").matcher(version);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static Integer firstInt(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }
}
