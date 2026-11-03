package com.smartdeploy.audit;

/**
 * Actions tracées dans le journal d'activité.
 */
public enum AuditAction {

    // Sécurité
    LOGIN_SUCCESS,
    LOGIN_FAILURE,

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
