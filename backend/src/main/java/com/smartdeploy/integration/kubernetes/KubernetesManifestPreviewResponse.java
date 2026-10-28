package com.smartdeploy.integration.kubernetes;

public record KubernetesManifestPreviewResponse(
        String deploymentYaml,
        String serviceYaml
) {
}