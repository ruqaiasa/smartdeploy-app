package com.smartdeploy.deployment;

import com.smartdeploy.integration.kubernetes.KubernetesApplicationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateDeploymentRequest(

        @NotNull(message = "L'identifiant de la version est obligatoire")
        Long versionId,

        @NotNull(message = "L'identifiant de l'environnement est obligatoire")
        Long environmentId,

        @Valid
        @NotNull(message = "La configuration Kubernetes est obligatoire")
        KubernetesApplicationRequest kubernetesConfig

) {

        // Compatibilité temporaire avec les anciens appels
        public CreateDeploymentRequest(
                Long versionId,
                Long environmentId
        ) {
                this(versionId, environmentId, null);
        }
}