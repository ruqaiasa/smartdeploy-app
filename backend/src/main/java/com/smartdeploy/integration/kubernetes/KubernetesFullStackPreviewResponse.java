package com.smartdeploy.integration.kubernetes;

public record KubernetesFullStackPreviewResponse(
        String frontendDeploymentYaml,
        String frontendServiceYaml,
        String backendDeploymentYaml,
        String backendServiceYaml
) {}