package com.smartdeploy.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateApplicationRequest(

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
        String name,

        @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères")
        String description,

        @NotBlank(message = "L'URL du repository est obligatoire")
        @Size(max = 500, message = "L'URL ne doit pas dépasser 500 caractères")
        String repositoryUrl,

        @NotNull(message = "La technologie est obligatoire")
        Technology technology

) {
}