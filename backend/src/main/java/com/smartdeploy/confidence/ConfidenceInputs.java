package com.smartdeploy.confidence;

import com.smartdeploy.environment.EnvironmentType;

/**
 * Faits utilisés par le score, rassemblés depuis l'historique de SmartDeploy.
 *
 * @param recentFinished        déploiements terminés récents sur l'environnement (10 au plus)
 * @param recentFailed          parmi eux, ceux qui ont échoué, hors échecs déjà comptés pour la version
 * @param versionFailures       échecs passés de cette version dus à son code (diagnostic de l'analyste)
 * @param versionDegraded       cette version a déjà été jugée dégradée par le gardien (hors retours arrière)
 * @param recentIncidents       mises en service d'autres versions jugées dégradées sur l'environnement depuis 24 heures
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
        String gitBranch,
        // Nombre de mises en service de cette version jugées dégradées par le gardien
        int versionDegradations
) {
    public ConfidenceInputs(int recentFinished, int recentFailed, int versionFailures, boolean versionDegraded,
                            int recentIncidents, boolean validatedInDevelopment, EnvironmentType environmentType,
                            String gitBranch) {
        this(recentFinished, recentFailed, versionFailures, versionDegraded, recentIncidents, validatedInDevelopment,
                environmentType, gitBranch, versionDegraded ? 1 : 0);
    }
}
