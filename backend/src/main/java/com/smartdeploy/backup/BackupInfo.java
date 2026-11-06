package com.smartdeploy.backup;

/**
 * Une sauvegarde Velero, telle que présentée dans SmartDeploy.
 *
 * @param origin    SCHEDULED (planifiée), PRE_DEPLOYMENT (avant un déploiement) ou MANUAL
 * @param phase     phase Velero : New, InProgress, Completed, PartiallyFailed, Failed…
 */
public record BackupInfo(
        String name,
        String origin,
        Long deploymentId,
        String phase,
        String startedAt,
        String completedAt,
        String expiresAt,
        Integer itemsBackedUp,
        Integer errors,
        Integer warnings
) {
}
