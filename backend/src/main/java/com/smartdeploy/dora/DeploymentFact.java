package com.smartdeploy.dora;

import java.time.LocalDateTime;

/**
 * Un déploiement, réduit aux faits utiles aux indicateurs DORA.
 *
 * @param outcome  SUCCESS (mis en service, y compris remplacé depuis), FAILED ou OTHER (en cours, annulé)
 * @param rollback vrai pour un retour arrière (une remédiation, pas un changement)
 * @param degraded vrai si le gardien l'a jugé dégradé après sa mise en service
 */
public record DeploymentFact(
        Long id,
        Long environmentId,
        String outcome,
        boolean rollback,
        boolean degraded,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
    boolean success() { return "SUCCESS".equals(outcome); }
    boolean failed() { return "FAILED".equals(outcome); }
}
