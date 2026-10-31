package com.smartdeploy.deployment;

public record PipelineCallbackResult(
        Long deploymentId,
        DeploymentStatus status,
        PipelineCallbackOutcome outcome
) {
}
