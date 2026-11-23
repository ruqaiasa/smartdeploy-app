package com.smartdeploy.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "L'adresse email est obligatoire")
        @Email(message = "L'adresse email n'est pas valide")
        @Size(max = 180)
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, max = 100)
        // RG-U2 : 8 caractères au moins, une majuscule, une minuscule, un chiffre et un caractère spécial
        @jakarta.validation.constraints.Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,100}$",
                message = "Le mot de passe doit contenir 8 caractères au moins, une majuscule, une minuscule, un chiffre et un caractère spécial")
        String password,

        // L'équipe et la raison de la demande, lues par l'administrateur avant validation
        @jakarta.validation.constraints.Size(max = 500)
        String accessRequest
) {
    public RegisterRequest(String firstName, String lastName, String email, String password) {
        this(firstName, lastName, email, password, null);
    }
}
