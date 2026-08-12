package com.smartdeploy.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank(message = "L'ancien mot de passe est obligatoire")
        String oldPassword,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(
                min = 8,
                max = 100,
                message = "Le nouveau mot de passe doit contenir entre 8 et 100 caractères"
        )
        String newPassword

) {
}