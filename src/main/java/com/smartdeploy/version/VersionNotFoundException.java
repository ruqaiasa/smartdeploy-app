package com.smartdeploy.version;

public class VersionNotFoundException extends RuntimeException {

    public VersionNotFoundException(Long id) {
        super("Version introuvable avec l'identifiant : " + id);
    }
}