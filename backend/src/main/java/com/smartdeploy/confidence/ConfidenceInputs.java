package com.smartdeploy.confidence;

import com.smartdeploy.environment.EnvironmentType;

/**
 * Faits utilisés par le score, rassemblés depuis l'historique de SmartDeploy.
 *
 * @param recentFinished        déploiements terminés récents sur l'environnement (10 au plus)
 * @param recentFailed          parmi eux, ceux qui ont échoué
 * @param versionFailures       échecs passés de cette version (tous environnements)
 * @param versionDegraded       cette version a déjà été jugée dégradée par le gardien
 * @param recentIncidents       incidents analysés sur l'environnement depuis 24 heures
 * @param validatedInDevelopment cette version a déjà réussi en développement ou en test
 */
public record ConfidenceInputs(
        int recentFinished,
        int recentFailed,
        int versionFailures,
        boolean versionDegraded,
        int recentIncidents,
        boolean validatedInDevelopment,
        EnvironmentType environmentType,
        String gitBranch
) {
}
