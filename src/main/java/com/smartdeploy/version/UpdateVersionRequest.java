package com.smartdeploy.version;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateVersionRequest(

        @NotBlank(message = "Le numéro de version est obligatoire")
        @Size(max = 50, message = "La version ne doit pas dépasser 50 caractères")
        String version,

        @NotBlank(message = "La branche Git est obligatoire")
        @Size(max = 150, message = "La branche Git ne doit pas dépasser 150 caractères")
        String gitBranch,

        @Size(max = 100, message = "Le commit Git ne doit pas dépasser 100 caractères")
        String gitCommit,

        @Size(max = 500, message = "L'image Docker ne doit pas dépasser 500 caractères")
        String dockerImage,

        @Size(max = 500, message = "Le chemin de l'artefact ne doit pas dépasser 500 caractères")
        String artifactPath,

        @Size(max = 1000, message = "L'URL des logs ne doit pas dépasser 1000 caractères")
        String buildLogsUrl

) {
}