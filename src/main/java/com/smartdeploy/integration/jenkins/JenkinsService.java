package com.smartdeploy.integration.jenkins;

import com.smartdeploy.deployment.Deployment;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JenkinsService {

    private final JenkinsClient jenkinsClient;

    public JenkinsService(
            JenkinsClient jenkinsClient
    ) {
        this.jenkinsClient = jenkinsClient;
    }

    /* ==========================================================
     *              TRIGGER DEPLOYMENT PIPELINE
     * ==========================================================
     */

    public JenkinsBuildResponse triggerDeployment(
            Deployment deployment
    ) {
        validateDeployment(deployment);

        Map<String, String> parameters =
                buildParameters(deployment);

        String queueUrl =
                jenkinsClient.triggerBuild(parameters);

        return new JenkinsBuildResponse(
                queueUrl,
                "Pipeline Jenkins déclenché avec succès"
        );
    }

    /* ==========================================================
     *                  BUILD JENKINS PARAMETERS
     * ==========================================================
     */

    private Map<String, String> buildParameters(
            Deployment deployment
    ) {
        Map<String, String> parameters =
                new LinkedHashMap<>();

        parameters.put(
                "DEPLOYMENT_ID",
                deployment.getId().toString()
        );

        parameters.put(
                "APPLICATION_ID",
                deployment.getVersion()
                        .getApplication()
                        .getId()
                        .toString()
        );

        parameters.put(
                "APPLICATION_NAME",
                deployment.getVersion()
                        .getApplication()
                        .getName()
        );

        parameters.put(
                "REPOSITORY_URL",
                deployment.getVersion()
                        .getApplication()
                        .getRepositoryUrl()
        );

        parameters.put(
                "VERSION_ID",
                deployment.getVersion()
                        .getId()
                        .toString()
        );

        parameters.put(
                "VERSION",
                deployment.getVersion()
                        .getVersion()
        );

        parameters.put(
                "GIT_BRANCH",
                deployment.getVersion()
                        .getGitBranch()
        );

        parameters.put(
                "GIT_COMMIT",
                deployment.getVersion()
                        .getGitCommit()
        );

        parameters.put(
                "DOCKER_IMAGE",
                deployment.getVersion()
                        .getDockerImage()
        );

        parameters.put(
                "ENVIRONMENT_ID",
                deployment.getEnvironment()
                        .getId()
                        .toString()
        );

        parameters.put(
                "ENVIRONMENT_TYPE",
                deployment.getEnvironment()
                        .getType()
                        .name()
        );

        parameters.put(
                "ENVIRONMENT_NAME",
                deployment.getEnvironment()
                        .getName()
        );

        parameters.put(
                "KUBERNETES_NAMESPACE",
                deployment.getEnvironment()
                        .getNamespace()
        );

        parameters.put(
                "CLUSTER_URL",
                deployment.getEnvironment()
                        .getClusterUrl()
        );

        return parameters;
    }

    /* ==========================================================
     *                      VALIDATION
     * ==========================================================
     */

    private void validateDeployment(
            Deployment deployment
    ) {
        if (deployment == null) {
            throw new JenkinsIntegrationException(
                    "Le déploiement à transmettre à Jenkins est absent"
            );
        }

        if (deployment.getId() == null) {
            throw new JenkinsIntegrationException(
                    "Le déploiement doit être sauvegardé avant "
                            + "de déclencher Jenkins"
            );
        }

        if (deployment.getVersion() == null) {
            throw new JenkinsIntegrationException(
                    "Aucune version n'est associée au déploiement"
            );
        }

        if (deployment.getVersion().getApplication() == null) {
            throw new JenkinsIntegrationException(
                    "Aucune application n'est associée à la version"
            );
        }

        if (deployment.getEnvironment() == null) {
            throw new JenkinsIntegrationException(
                    "Aucun environnement n'est associé au déploiement"
            );
        }

        if (isBlank(
                deployment.getVersion()
                        .getApplication()
                        .getRepositoryUrl()
        )) {
            throw new JenkinsIntegrationException(
                    "L'URL du dépôt Git de l'application est absente"
            );
        }

        if (isBlank(
                deployment.getVersion().getGitBranch()
        )) {
            throw new JenkinsIntegrationException(
                    "La branche Git de la version est absente"
            );
        }

        if (isBlank(
                deployment.getEnvironment().getNamespace()
        )) {
            throw new JenkinsIntegrationException(
                    "Le namespace Kubernetes de l'environnement "
                            + "est absent"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}