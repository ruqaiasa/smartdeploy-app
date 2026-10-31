package com.smartdeploy.integration.jenkins;

/**
 * Résultat d’une demande d’annulation transmise à Jenkins.
 *
 * @param jenkinsReached false si Jenkins n’a pas pu être joint
 * @param message        explication lisible, conservée dans le déploiement
 */
public record JenkinsCancellationResult(
        boolean jenkinsReached,
        String message
) {
}
