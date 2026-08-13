package com.smartdeploy.user;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(

        @NotNull(message = "Le statut est obligatoire")
        Boolean enabled

) {
}