package com.smartdeploy.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateApplicationRequest(

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 150)
        String name,

        @Size(max = 1000)
        String description,

        @NotBlank(message = "Le repository est obligatoire")
        @Size(max = 500)
        String repositoryUrl,

        @NotNull(message = "La technologie est obligatoire")
        Technology technology

) {
}