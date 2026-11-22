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
        KubernetesApplicationRequest kubernetesConfig,

        // RG-V5 : confirmation explicite pour redéployer la version déjà en service
        Boolean confirmRedeploy,

        // RG-V4 : reconstruire l'image même si la version a déjà été construite
        Boolean forceRebuild,

        // RG-P5 : justification d'une exception d'urgence (administrateur seulement)
        String emergencyJustification,

        // Portail développeur : message joint à une demande de déploiement
        @jakarta.validation.constraints.Size(max = 500, message = "Le message est limité à 500 caractères")
        String requestMessage
) {
        /** Compatibilité : sans message de demande. */
        public CreateDeploymentRequest(Long versionId, Long environmentId, KubernetesApplicationRequest kubernetesConfig,
                                       Boolean confirmRedeploy, Boolean forceRebuild, String emergencyJustification) {
                this(versionId, environmentId, kubernetesConfig, confirmRedeploy, forceRebuild, emergencyJustification, null);
        }

        public CreateDeploymentRequest(
                Long versionId,
                Long environmentId,
                KubernetesApplicationRequest kubernetesConfig
        ) {
                this(versionId, environmentId, kubernetesConfig, null, null, null);
        }

        // Compatibilité temporaire avec les anciens appels
        public CreateDeploymentRequest(
                Long versionId,
                Long environmentId
        ) {
                this(versionId, environmentId, null, null, null, null);
        }
}
