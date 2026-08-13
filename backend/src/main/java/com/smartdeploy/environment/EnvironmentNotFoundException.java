package com.smartdeploy.environment;

public class EnvironmentNotFoundException extends RuntimeException {

    public EnvironmentNotFoundException(Long id) {
        super("Environnement introuvable avec l'identifiant : " + id);
    }
}