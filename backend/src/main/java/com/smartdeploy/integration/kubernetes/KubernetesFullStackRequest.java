package com.smartdeploy.integration.kubernetes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record KubernetesFullStackRequest(

        @NotBlank
        @Pattern(regexp = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")
        String namespace,

        @NotNull
        @Valid
        KubernetesComponentRequest frontend,

        @NotNull
        @Valid
        KubernetesComponentRequest backend

) {}