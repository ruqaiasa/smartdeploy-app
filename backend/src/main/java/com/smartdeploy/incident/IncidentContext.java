package com.smartdeploy.incident;

/**
 * Données d'un déploiement utiles à l'analyse, extraites dans une transaction :
 * l'analyse s'exécute ensuite hors transaction (appels à Jenkins et Kubernetes).
 */
public record IncidentContext(
        Long deploymentId,
        String applicationName,
        String environmentName,
        String version,
        String namespace,
        String applicationPrefix,
        Integer jenkinsBuildNumber,
        String errorMessage
) {
}
