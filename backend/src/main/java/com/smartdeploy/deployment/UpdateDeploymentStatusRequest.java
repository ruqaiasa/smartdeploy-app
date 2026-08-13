package com.smartdeploy.deployment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDeploymentStatusRequest(

        @NotNull(message = "Le statut est obligatoire")
        DeploymentStatus status,

        @Size(
                max = 2000,
                message = "Le message d'erreur ne doit pas dépasser 2000 caractères"
        )
        String errorMessage

) {
}