package com.smartdeploy.integration.jenkins;

import com.smartdeploy.deployment.DeploymentStatus;

public record JenkinsWebhookRequest(

        Long deploymentId,

        DeploymentStatus status,

        Integer buildNumber,

        String logsUrl,

        String errorMessage,

        String kubernetesDeploymentName,

        // Images publiées, par empreinte (absentes avec un ancien Jenkinsfile)
        String backendImage,
        String frontendImage,
        // Commit exact construit (git rev-parse HEAD), absent avec un ancien Jenkinsfile
        String gitCommit
) {
}