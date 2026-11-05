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

    // Analyste d'incidents
    INCIDENT_ANALYZED,

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
