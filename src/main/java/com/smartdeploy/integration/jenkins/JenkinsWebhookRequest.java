package com.smartdeploy.integration.jenkins;

import com.smartdeploy.deployment.DeploymentStatus;

public record JenkinsWebhookRequest(

        Long deploymentId,

        DeploymentStatus status,

        Integer buildNumber,

        String logsUrl,

        String errorMessage

) {
}