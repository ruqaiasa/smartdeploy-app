package com.smartdeploy.auth.reset;

/** Lien de réinitialisation invalide, expiré ou déjà utilisé, ou mot de passe refusé. */
public class PasswordResetException extends RuntimeException {
    public PasswordResetException(String message) {
        super(message);
    }
}
