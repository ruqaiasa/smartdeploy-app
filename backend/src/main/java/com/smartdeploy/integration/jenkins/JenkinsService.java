package com.smartdeploy.integration.jenkins;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentKubernetesConfigRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.smartdeploy.deployment.DeploymentKubernetesConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
@Service
public class JenkinsService {

    private final JenkinsClient jenkinsClient;
    private final DeploymentKubernetesConfigRepository kubernetesConfigRepository;


    private final ObjectMapper objectMapper;
    public JenkinsService(
            JenkinsClient jenkinsClient,
            DeploymentKubernetesConfigRepository kubernetesConfigRepository,
            ObjectMapper objectMapper
    ) {
        this.jenkinsClient = jenkinsClient;
        this.kubernetesConfigRepository = kubernetesConfigRepository;
        this.objectMapper = objectMapper;
    }

    /* ==========================================================
     * TRIGGER DEPLOYMENT PIPELINE
     * ========================================================== */

    public JenkinsBuildResponse triggerDeployment(Deployment deployment) {

        validateDeployment(deployment);

        Map<String, String> parameters = buildParameters(deployment);

        String queueUrl = jenkinsClient.triggerBuild(parameters);

        return new JenkinsBuildResponse(
                queueUrl,
                "Pipeline Jenkins dÃ©clenchÃ© avec succÃ¨s"
        );
    }

    /* ==========================================================
     * BUILD JENKINS PARAMETERS
     * ========================================================== */

    private Map<String, String> buildParameters(Deployment deployment) {

        var version = deployment.getVersion();
        var application = version.getApplication();
        var environment = deployment.getEnvironment();

        Map<String, String> parameters = new LinkedHashMap<>();

        // DEPLOYMENT
        parameters.put("DEPLOYMENT_ID", deployment.getId().toString());

        // APPLICATION
        parameters.put("APPLICATION_ID", application.getId().toString());
        parameters.put("APPLICATION_NAME", application.getName());
        parameters.put("REPOSITORY_URL", application.getRepositoryUrl());

        // VERSION
        parameters.put("VERSION_ID", version.getId().toString());
        parameters.put("VERSION", version.getVersion());
        parameters.put("GIT_BRANCH", version.getGitBranch());
        parameters.put("GIT_COMMIT", value(version.getGitCommit()));
        parameters.put("DOCKER_IMAGE", value(version.getDockerImage()));

        // ENVIRONMENT
        parameters.put("ENVIRONMENT_ID", environment.getId().toString());
        parameters.put("ENVIRONMENT_TYPE", environment.getType().name());
        parameters.put("ENVIRONMENT_NAME", environment.getName());

        // KUBERNETES
        parameters.put("KUBERNETES_NAMESPACE", environment.getNamespace());
        parameters.put("CLUSTER_URL", value(environment.getClusterUrl()));

        DeploymentKubernetesConfig config = kubernetesConfigRepository
                .findByDeploymentId(deployment.getId())
                .orElseThrow(() -> new JenkinsIntegrationException(
                        "Configuration Kubernetes introuvable pour le dÃ©ploiement "
                                + deployment.getId()
                ));

        try {
            JsonNode root = objectMapper.readTree(config.getConfigurationJson());

            JsonNode frontend = root.path("frontend");
            JsonNode backend = root.path("backend");
            JsonNode database = root.path("database");

            boolean hasFrontend = frontend.isObject() && !frontend.isNull();

            parameters.put(
                    "DEPLOYMENT_MODE",
                    hasFrontend ? "FULL_STACK" : "BACKEND_ONLY"
            );

            parameters.put(
                    "KUBERNETES_NAMESPACE",
                    config.getNamespace()
            );

            parameters.put(
                    "FRONTEND_IMAGE",
                    frontend.path("image").asText("")
            );

            // ==========================================================
// BACKEND IMAGE
// ==========================================================

            String configuredBackendImage =
                    backend.path("image").asText("").trim();

            parameters.put(
                    "BACKEND_IMAGE",
                    configuredBackendImage
            );

// L'image Kubernetes est prioritaire sur l'image historique
// enregistrée dans la version.
            if (!configuredBackendImage.isEmpty()) {
                parameters.put(
                        "DOCKER_IMAGE",
                        configuredBackendImage
                );
            }

// ==========================================================
// REPLICAS
// ==========================================================

            parameters.put(
                    "FRONTEND_REPLICAS",
                    String.valueOf(frontend.path("replicas").asInt(0))
            );

            parameters.put(
                    "BACKEND_REPLICAS",
                    String.valueOf(backend.path("replicas").asInt(1))
            );

// ==========================================================
// DATABASE
// ==========================================================

            parameters.put(
                    "DATABASE_MODE",
                    database.path("mode").asText("EXTERNAL")
            );

            // CI/CD : paramètres provenant de la configuration sauvegardée.
            JsonNode cicd = root.path("cicd");
            String strategy = textOrDefault(cicd, "buildStrategy", "MAVEN");
            String sonarMode = textOrDefault(cicd, "sonarMode", "MAVEN");
            if (!java.util.Set.of("MAVEN", "DOCKER_ONLY").contains(strategy)) {
                throw new JenkinsIntegrationException("BUILD_STRATEGY invalide : " + strategy);
            }
            if (!java.util.Set.of("MAVEN", "CLI", "SKIP").contains(sonarMode)) {
                throw new JenkinsIntegrationException("SONAR_MODE invalide : " + sonarMode);
            }
            if (strategy.equals("DOCKER_ONLY") && sonarMode.equals("MAVEN")) {
                throw new JenkinsIntegrationException("DOCKER_ONLY nécessite SONAR_MODE CLI ou SKIP");
            }
            parameters.put("BUILD_STRATEGY", strategy);
            parameters.put("SONAR_MODE", sonarMode);
            parameters.put("RUN_BACKEND_TESTS", Boolean.toString(cicd.path("runBackendTests").asBoolean(false)));
            parameters.put("BACKEND_BUILD_CONTEXT", safeRelativePath(textOrDefault(backend, "buildContext", ".")));
            parameters.put("BACKEND_DOCKERFILE_PATH", safeRelativePath(textOrDefault(backend, "dockerfilePath", "Dockerfile")));

            if (hasFrontend) {
                parameters.put("FRONTEND_REPOSITORY_URL", textOrDefault(frontend, "repositoryUrl", application.getRepositoryUrl()));
                parameters.put("FRONTEND_GIT_BRANCH", textOrDefault(frontend, "gitBranch", version.getGitBranch()));
                parameters.put("FRONTEND_BUILD_CONTEXT", safeRelativePath(textOrDefault(frontend, "buildContext", ".")));
                parameters.put("FRONTEND_DOCKERFILE_PATH", safeRelativePath(textOrDefault(frontend, "dockerfilePath", "Dockerfile")));
            }

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new JenkinsIntegrationException(
                    "Configuration Kubernetes JSON invalide pour le dÃ©ploiement "
                            + deployment.getId()
            );
        }
        return parameters;
    }

    /* ==========================================================
     * UTILITIES
     * ========================================================== */

    private String textOrDefault(JsonNode node, String field, String fallback) {
        String text = node.path(field).asText("").trim();
        return text.isEmpty() ? fallback : text;
    }

    // Validation minimale : chemins relatifs uniquement, sans traversée de répertoires.
    private String safeRelativePath(String path) {
        String normalized = path.replace('\\', '/').trim();
        if (normalized.isEmpty() || normalized.startsWith("/") ||
                normalized.equals("..") || normalized.startsWith("../") ||
                normalized.contains("/../") || normalized.endsWith("/..") ||
                normalized.matches("^[A-Za-z]:.*")) {
            throw new JenkinsIntegrationException("Chemin de build non autorisé : " + path);
        }
        return normalized;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /* ==========================================================
     * VALIDATION
     * ========================================================== */

    private void validateDeployment(Deployment deployment) {

        if (deployment == null) {
            throw new JenkinsIntegrationException(
                    "Le dÃ©ploiement Ã  transmettre Ã  Jenkins est absent"
            );
        }

        if (deployment.getId() == null) {
            throw new JenkinsIntegrationException(
                    "Le dÃ©ploiement doit Ãªtre sauvegardÃ© avant de dÃ©clencher Jenkins"
            );
        }

        if (deployment.getVersion() == null) {
            throw new JenkinsIntegrationException(
                    "Aucune version n'est associÃ©e au dÃ©ploiement"
            );
        }

        if (deployment.getVersion().getApplication() == null) {
            throw new JenkinsIntegrationException(
                    "Aucune application n'est associÃ©e Ã  la version"
            );
        }

        if (deployment.getEnvironment() == null) {
            throw new JenkinsIntegrationException(
                    "Aucun environnement n'est associÃ© au dÃ©ploiement"
            );
        }

        if (isBlank(
                deployment.getVersion()
                        .getApplication()
                        .getRepositoryUrl()
        )) {
            throw new JenkinsIntegrationException(
                    "L'URL du dÃ©pÃ´t Git de l'application est absente"
            );
        }

        if (isBlank(deployment.getVersion().getGitBranch())) {
            throw new JenkinsIntegrationException(
                    "La branche Git de la version est absente"
            );
        }

        if (isBlank(deployment.getEnvironment().getNamespace())) {
            throw new JenkinsIntegrationException(
                    "Le namespace Kubernetes de l'environnement est absent"
            );
        }
    }
}