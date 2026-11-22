package com.smartdeploy.portal;

import com.smartdeploy.portal.CompatibilityReport.Status;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Contrôle de compatibilité, sur un dépôt identique à celui de l'application de démonstration. */
class CompatibilityAnalyzerTest {

    // Fichiers réels de l'application de démonstration (backend Spring Boot, interface React, PostgreSQL)
    static final String POM = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n	xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n	<modelVersion>4.0.0</modelVersion>\n	<parent>\n		<groupId>org.springframework.boot</groupId>\n		<artifactId>spring-boot-starter-parent</artifactId>\n		<version>4.1.2</version>\n		<relativePath/> <!-- lookup parent from repository -->\n	</parent>\n	<groupId>com.spring</groupId>\n	<artifactId>render</artifactId>\n	<version>0.0.1-SNAPSHOT</version>\n	<name>render</name>\n	<description>Demo project for Spring Boot</description>\n	<url/>\n	<licenses>\n		<license/>\n	</licenses>\n	<developers>\n		<developer/>\n	</developers>\n	<scm>\n		<connection/>\n		<developerConnection/>\n		<tag/>\n		<url/>\n	</scm>\n	<properties>\n		<java.version>21</java.version>\n	</properties>\n	<dependencies>\n		<dependency>\n			<groupId>org.springframework.boot</groupId>\n			<artifactId>spring-boot-starter-data-jpa</artifactId>\n		</dependency>\n		<dependency>\n			<groupId>org.springframework.boot</groupId>\n			<artifactId>spring-boot-starter-webmvc</artifactId>\n		</dependency>\n\n		<dependency>\n			<groupId>org.postgresql</groupId>\n			<artifactId>postgresql</artifactId>\n			<scope>runtime</scope>\n		</dependency>\n		<dependency>\n			<groupId>org.springframework.boot</groupId>\n			<artifactId>spring-boot-starter-test</artifactId>\n			<scope>test</scope>\n		</dependency>\n	</dependencies>\n\n	<build>\n		<plugins>\n			<plugin>\n				<groupId>org.springframework.boot</groupId>\n				<artifactId>spring-boot-maven-plugin</artifactId>\n			</plugin>\n		</plugins>\n	</build>\n\n</project>\n";
    static final String PROPERTIES = "spring.application.name=render\n\nspring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/database}\nspring.datasource.username=user\nspring.datasource.password=1234\n\nspring.jpa.hibernate.ddl-auto=update\n\ncors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173}";
    static final String DOCKERFILE = "# =====================================================================\n# Backend Spring Boot : image en deux étapes (SmartDeploy, PFE)\n# 1. Construction avec le JDK ; les dépendances Maven sont téléchargées\n#    dans une couche séparée, réutilisée tant que pom.xml ne change pas.\n# 2. Exécution avec le JRE seul, sans code source ni cache Maven,\n#    par un utilisateur non privilégié.\n# =====================================================================\nFROM eclipse-temurin:21-jdk-alpine AS build\nWORKDIR /workspace\n\nCOPY .mvn .mvn\nCOPY mvnw pom.xml ./\nRUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline\n\nCOPY src src\nRUN ./mvnw -B -q package -DskipTests \\\n && cp target/*.jar app.jar\n\nFROM eclipse-temurin:21-jre-alpine\nWORKDIR /app\n\nRUN addgroup -S app && adduser -S app -G app\nCOPY --from=build --chown=app:app /workspace/app.jar app.jar\nUSER app\n\nEXPOSE 8080\n# La JVM adapte sa mémoire à la limite du conteneur.\nENTRYPOINT [\"java\", \"-XX:MaxRAMPercentage=75\", \"-jar\", \"/app/app.jar\"]\n";

    private static RepositorySnapshot demo() {
        Map<String, String> files = new HashMap<>();
        files.put("backend/pom.xml", POM);
        files.put("backend/Dockerfile", DOCKERFILE);
        files.put("backend/src/main/resources/application.properties", PROPERTIES);
        return new RepositorySnapshot("ruqaiasa/fullstack-app-deploy-render", "master",
                Set.of("backend/pom.xml", "backend/mvnw", "backend/Dockerfile", "backend/src/main/resources/application.properties",
                        "backend/src/test/java/com/spring/render/RenderApplicationTests.java",
                        "frontend/package.json", "frontend/Dockerfile"), files);
    }

    private static CompatibilityReport.Check check(CompatibilityReport r, String id) {
        return r.checks().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theDemoApplicationIsAlmostReadyBecauseOfAPasswordInClear() {
        CompatibilityReport report = CompatibilityAnalyzer.analyze(demo());

        assertEquals("backend", report.backendDirectory());
        assertEquals("frontend", report.frontendDirectory());
        assertEquals(Status.OK, check(report, "mvnw").status());
        assertEquals(Status.OK, check(report, "spring-boot").status());
        assertEquals(Status.OK, check(report, "dockerfile").status());
        assertEquals(Status.OK, check(report, "port").status(), check(report, "port").detail());
        assertEquals(Status.WARNING, check(report, "secrets").status(), "spring.datasource.password=1234 est signalé");
        assertEquals("FULLSTACK_MANAGED", report.recommendedArchitecture(), "interface et PostgreSQL : application complète, base gérée");
        assertEquals("ALMOST_READY", report.verdict());
    }

    @Test
    void aRepositoryWithoutMavenProjectIsNotReady() {
        RepositorySnapshot empty = new RepositorySnapshot("equipe/vide", "main", Set.of("README.md"), Map.of());

        CompatibilityReport report = CompatibilityAnalyzer.analyze(empty);

        assertEquals("NOT_READY", report.verdict());
        assertEquals(Status.ERROR, check(report, "projet").status());
        assertNull(report.recommendedArchitecture());
    }

    @Test
    void aMissingWrapperOrDockerfileBlocksAndExplainsTheFix() {
        RepositorySnapshot incomplete = new RepositorySnapshot("equipe/api", "main", Set.of("pom.xml"),
                Map.of("pom.xml", "<project><parent><artifactId>spring-boot-starter-parent</artifactId><version>2.7.18</version></parent></project>"));

        CompatibilityReport report = CompatibilityAnalyzer.analyze(incomplete);

        assertEquals(Status.ERROR, check(report, "mvnw").status());
        assertNotNull(check(report, "mvnw").fix());
        assertEquals(Status.ERROR, check(report, "dockerfile").status());
        assertEquals(Status.WARNING, check(report, "spring-boot").status(), "Spring Boot 2 n'est plus maintenu");
        assertEquals("BACKEND", report.recommendedArchitecture());
    }
}
