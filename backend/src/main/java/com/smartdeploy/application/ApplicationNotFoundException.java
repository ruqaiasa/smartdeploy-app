package com.smartdeploy.application;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(Long id) {
        super("Application introuvable avec l'identifiant : " + id);
    }
}