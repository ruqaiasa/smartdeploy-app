package com.smartdeploy.deployment;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateDeploymentMetadataRequest(

        @PositiveOrZero(
                message = "Le numéro du build doit être positif ou nul"
        )
        Integer jenkinsBuildNumber,

        @Size(
                max = 1000,
                message = "L'URL des logs ne doit pas dépasser 1000 caractères"
        )
        String deploymentLogsUrl,

        @Size(
                max = 255,
                message = "Le nom du déploiement Kubernetes ne doit pas dépasser 255 caractères"
        )
        String kubernetesDeploymentName

) {
}
