package com.smartdeploy.audit;

/**
 * Événement d'audit publié par les services métier.
 *
 * @param actor          auteur de l'action ; null = utilisateur authentifié
 *                       de la requête en cours, ou « Système »
 * @param keepOnRollback vrai pour un événement à conserver même si la
 *                       transaction échoue (ex. échec de connexion)
 */
public record AuditEvent(
        AuditAction action,
        String actor,
        String targetType,
        Long targetId,
        String applicationName,
        String environmentName,
        String summary,
        boolean keepOnRollback
) {

    public static AuditEvent of(
            AuditAction action, String actor, String targetType, Long targetId,
            String applicationName, String environmentName, String summary
    ) {
        return new AuditEvent(action, actor, targetType, targetId,
                applicationName, environmentName, summary, false);
    }

    public static AuditEvent securityFailure(AuditAction action, String actor, String summary) {
        return new AuditEvent(action, actor, "USER", null, null, null, summary, true);
    }
}
