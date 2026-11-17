package com.smartdeploy.deployment;

import java.time.LocalDateTime;

/**
 * Vérification préalable d'un déploiement (RG-V4 à RG-V8) : ce que l'utilisateur doit savoir
 * avant de lancer, affiché à l'étape « Vérification » de l'assistant.
 *
 * @param sameVersionInService RG-V5 : cette version est déjà en service sur l'environnement
 * @param olderThanCurrent     RG-V6 : elle est plus ancienne que la version en service
 * @param rollbackTargetId     RG-V6 : déploiement sain de cette version, utilisable par « Revenir ici »
 * @param degraded             RG-V7 : dernière dégradation de cette version, s'il y en a eu
 * @param lastFailure          RG-V8 : dernier échec de cette version, s'il est postérieur à sa dernière réussite
 * @param reuse                RG-V4 : déploiement dont l'image sera réutilisée, sans reconstruction
 * @param pinnedCommit         RG-V2 : commit sur lequel la version est figée, s'il est connu
 */
public record DeploymentPrecheck(
        Long currentDeploymentId,
        String currentVersion,
        LocalDateTime currentSince,
        boolean sameVersionInService,
        boolean olderThanCurrent,
        Long rollbackTargetId,
        Degraded degraded,
        LastFailure lastFailure,
        Reuse reuse,
        String pinnedCommit
) {
    public record Degraded(Long deploymentId, String environmentName, LocalDateTime at, String cause) {
    }

    /**
     * @param unchangedSinceFailure vrai : le code n'a pas changé depuis l'échec ; null : inconnu
     * @param unverifiableReason    pourquoi la comparaison est impossible, quand elle l'est
     */
    public record LastFailure(Long deploymentId, LocalDateTime at, String title, String detail,
                              boolean codeCause, Boolean unchangedSinceFailure, String unverifiableReason) {
    }

    public record Reuse(Long deploymentId, String environmentName, String commit) {
    }
}
