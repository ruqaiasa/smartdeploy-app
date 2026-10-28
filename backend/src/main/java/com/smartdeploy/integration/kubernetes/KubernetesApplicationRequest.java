package com.smartdeploy.integration.kubernetes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record KubernetesApplicationRequest(

        @NotBlank
        String namespace,

        @Valid
        KubernetesComponentRequest frontend,

        @Valid
        KubernetesComponentRequest backend,

        @Valid
        KubernetesDatabaseRequest database,

        @Valid
        KubernetesIngressRequest ingress,

        @Valid
        KubernetesCiCdRequest cicd

) {

        // Compatibilité avec les constructeurs à cinq arguments.
        public KubernetesApplicationRequest(
                String namespace,
                KubernetesComponentRequest frontend,
                KubernetesComponentRequest backend,
                KubernetesDatabaseRequest database,
                KubernetesIngressRequest ingress
        ) {
                this(
                        namespace,
                        frontend,
                        backend,
                        database,
                        ingress,
                        null
                );
        }
}