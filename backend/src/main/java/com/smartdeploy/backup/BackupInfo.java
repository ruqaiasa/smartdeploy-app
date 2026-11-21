package com.smartdeploy.backup;

/**
 * Une sauvegarde Velero, telle que présentée dans SmartDeploy.
 *
 * @param protectedVersion version en service au moment d'un point de restauration (ce qu'il permet de retrouver)
 * @param origin    SCHEDULED (planifiée), PRE_DEPLOYMENT (avant un déploiement) ou MANUAL
 * @param phase     phase Velero : New, InProgress, Completed, PartiallyFailed, Failed…
 */
public record BackupInfo(
        String name,
        String origin,
        Long deploymentId,
        String protectedVersion,
        String phase,
        String startedAt,
        String completedAt,
        String expiresAt,
        Integer itemsBackedUp,
        Integer errors,
        Integer warnings,
        // Espaces de noms inclus : vide = tout le cluster (sauvegarde de la nuit)
        java.util.List<String> includedNamespaces
) {
    /** Compatibilité : sans la liste des espaces de noms. */
    public BackupInfo(String name, String origin, Long deploymentId, String protectedVersion, String phase, String startedAt,
                      String completedAt, String expiresAt, Integer itemsBackedUp, Integer errors, Integer warnings) {
        this(name, origin, deploymentId, protectedVersion, phase, startedAt, completedAt, expiresAt, itemsBackedUp,
                errors, warnings, java.util.List.of());
    }
}
