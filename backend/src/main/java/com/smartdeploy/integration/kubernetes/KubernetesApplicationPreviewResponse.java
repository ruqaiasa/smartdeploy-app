package com.smartdeploy.integration.kubernetes;

import java.util.Map;

public record KubernetesApplicationPreviewResponse(
        Map<String, String> manifests
) {}