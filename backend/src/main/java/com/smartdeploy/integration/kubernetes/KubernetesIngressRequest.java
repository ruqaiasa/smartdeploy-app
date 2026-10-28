package com.smartdeploy.integration.kubernetes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record KubernetesIngressRequest(

        @NotBlank
        @Pattern(
                regexp = "^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$"
        )
        String host,

        String ingressClassName,

        String tlsSecretName

) {}