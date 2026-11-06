package com.smartdeploy.backup;

/** Un déploiement attend son point de restauration avant le lancement du pipeline. */
public record RestorePointRequestedEvent(Long deploymentId) {
}
