package com.smartdeploy.version;

import jakarta.validation.constraints.NotNull;

public record UpdateBuildStatusRequest(

        @NotNull(message = "Le statut du build est obligatoire")
        BuildStatus buildStatus

) {
}