package com.smartdeploy.audit;

/**
 * Actions tracées dans le journal d'activité.
 */
public enum AuditAction {

    // Sécurité
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    ACCESS_DENIED,
    API_KEY_REJECTED,
    SUSPICIOUS_ACTIVITY,

    // Administration (applications, environnements, versions, utilisateurs, secrets)
    RESOURCE_CREATED,
    RESOURCE_UPDATED,
    RESOURCE_DELETED,
    USER_ROLE_CHANGED,
    USER_STATUS_CHANGED,
    ACTION_FAILED,

    // Supervision
    ALERT_OPENED,
    ALERT_RESOLVED,

    // Gardien de déploiement
    GUARD_PASSED,
    GUARD_DEGRADED,
    GUARD_INCONCLUSIVE,
    GUARD_AUTO_ROLLBACK,

    // Analyste d'incidents
    INCIDENT_ANALYZED,

    // Sauvegardes et restaurations (Velero)
    BACKUP_REQUESTED,
    BACKUP_COMPLETED,
    BACKUP_FAILED,
    RESTORE_STARTED,
    RESTORE_COMPLETED,
    RESTORE_FAILED,

    // Santé de l'infrastructure (RAID de la machine du cluster)
    RAID_DEGRADED,
    RAID_RECOVERING,
    RAID_RESTORED,

    // Mot de passe oublié
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,

    // Approbation avant la production
    DEPLOYMENT_APPROVAL_REQUESTED,
    DEPLOYMENT_APPROVED,
    DEPLOYMENT_REJECTED,
    DEPLOYMENT_APPROVAL_EXPIRED,

    // Promotion entre environnements : exception d'urgence (RG-P5)
    DEPLOYMENT_EMERGENCY_OVERRIDE,

    // Cycle de vie d'un déploiement
    DEPLOYMENT_CREATED,
    DEPLOYMENT_STARTED,
    DEPLOYMENT_SUCCEEDED,
    DEPLOYMENT_FAILED,
    DEPLOYMENT_CANCELLED,
    DEPLOYMENT_ROLLBACK_REQUESTED,
    DEPLOYMENT_REPLACED,
    DEPLOYMENT_EXPIRED,
    DEPLOYMENT_DELETED
}
