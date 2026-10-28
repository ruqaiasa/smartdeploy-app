package com.smartdeploy.integration.kubernetes;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record KubernetesComponentRequest(

        @NotBlank
        @Pattern(regexp = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")
        String name,

        String image,

        @Min(1)
        @Max(10)
        int replicas,

        @Min(1)
        @Max(65535)
        int containerPort,

        String repositoryUrl,

        String gitBranch,

        String dockerfilePath,

        String buildContext

) {

        // Compatibilité avec les appels existants à quatre arguments.
        public KubernetesComponentRequest(
                String name,
                String image,
                int replicas,
                int containerPort
        ) {
                this(
                        name,
                        image,
                        replicas,
                        containerPort,
                        null,
                        null,
                        null,
                        null
                );
        }
}