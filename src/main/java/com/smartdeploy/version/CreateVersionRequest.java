package com.smartdeploy.version;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVersionRequest(

        @NotNull(message = "L'identifiant de l'application est obligatoire")
        Long applicationId,

        @NotBlank(message = "Le numéro de version est obligatoire")
        @Size(max = 50)
        String version,

        @NotBlank(message = "La branche Git est obligatoire")
        @Size(max = 150)
        String gitBranch,

        @Size(max = 100)
        String gitCommit,

        @Size(max = 500)
        String dockerImage,

        @Size(max = 500)
        String artifactPath,

        @Size(max = 1000)
        String buildLogsUrl

) {
}