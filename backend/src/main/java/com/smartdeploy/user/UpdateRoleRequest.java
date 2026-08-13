package com.smartdeploy.user;

import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(

        @NotNull(message = "Le rôle est obligatoire")
        Role role

) {
}