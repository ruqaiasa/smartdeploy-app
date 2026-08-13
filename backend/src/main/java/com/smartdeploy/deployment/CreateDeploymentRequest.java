package com.smartdeploy.deployment;

import jakarta.validation.constraints.NotNull;

public record CreateDeploymentRequest(

        @NotNull(message = "L'identifiant de la version est obligatoire")
        Long versionId,

        @NotNull(message = "L'identifiant de l'environnement est obligatoire")
        Long environmentId

) {
}