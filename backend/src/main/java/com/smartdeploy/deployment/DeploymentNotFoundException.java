package com.smartdeploy.deployment;

public class DeploymentNotFoundException extends RuntimeException {

    public DeploymentNotFoundException(Long id) {
        super("Déploiement introuvable avec l'identifiant : " + id);
    }
}