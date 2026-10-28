package com.smartdeploy.integration.kubernetes;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record KubernetesDeploymentConfigRequest(

        @NotBlank(message = "Le nom de l'application est obligatoire")
        @Pattern(
                regexp = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$",
                message = "Le nom doit contenir uniquement des lettres minuscules, chiffres et tirets"
        )
        String applicationName,

        @NotBlank(message = "Le namespace Kubernetes est obligatoire")
        @Pattern(
                regexp = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$",
                message = "Le namespace doit contenir uniquement des lettres minuscules, chiffres et tirets"
        )
        String namespace,

        @NotBlank(message = "L'image Docker est obligatoire")
        String image,

        @Min(value = 1, message = "Le nombre de replicas doit être au minimum 1")
        @Max(value = 10, message = "Le nombre de replicas ne peut pas dépasser 10")
        int replicas,

        @Min(value = 1, message = "Le port doit être supérieur à 0")
        @Max(value = 65535, message = "Le port doit être inférieur ou égal à 65535")
        int containerPort

) {
}