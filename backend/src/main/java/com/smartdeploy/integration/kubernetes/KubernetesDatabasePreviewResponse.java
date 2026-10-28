package com.smartdeploy.integration.kubernetes;

public record KubernetesDatabasePreviewResponse(
        String statefulSetYaml,
        String serviceYaml
) {}