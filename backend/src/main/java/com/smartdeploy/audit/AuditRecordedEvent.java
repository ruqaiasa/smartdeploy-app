package com.smartdeploy.audit;

/**
 * Publié après l'enregistrement effectif d'une entrée du journal
 * (notifications, et plus tard AIOps et indicateurs DORA).
 */
public record AuditRecordedEvent(AuditLog entry) {
}
