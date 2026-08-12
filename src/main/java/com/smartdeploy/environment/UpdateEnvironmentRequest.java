package com.smartdeploy.environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateEnvironmentRequest(

        @NotNull
        Long applicationId,

        @NotNull
        EnvironmentType type,

        @NotBlank
        String name,

        String description,

        @NotBlank
        String namespace,

        String clusterUrl

) {
}