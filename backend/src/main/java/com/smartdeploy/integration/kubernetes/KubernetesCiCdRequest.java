package com.smartdeploy.integration.kubernetes;

public record KubernetesCiCdRequest(
        String buildStrategy,
        String sonarMode,
        Boolean runBackendTests
) {}