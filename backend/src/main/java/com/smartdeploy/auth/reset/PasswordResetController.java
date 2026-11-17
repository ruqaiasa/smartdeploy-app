package com.smartdeploy.auth.reset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Parcours « mot de passe oublié » (routes publiques). Le jeton voyage toujours dans le corps
 * d'une requête POST, jamais dans une adresse appelée sur le serveur (qui finirait dans les journaux).
 */
@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record TokenRequest(@NotBlank String token) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
    }

    static final String GENERIC_MESSAGE =
            "Si un compte actif correspond à cette adresse, un e-mail de réinitialisation vient d'être envoyé.";

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        service.request(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", GENERIC_MESSAGE));
    }

    @PostMapping("/reset-password/validate")
    public Map<String, Boolean> validate(@Valid @RequestBody TokenRequest request) {
        return Map.of("valid", service.isValid(request.token()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> reset(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            service.reset(request.token(), request.newPassword());
            return ResponseEntity.ok(Map.of("message", "Mot de passe modifié : vous pouvez vous connecter."));
        } catch (PasswordResetException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }
}
