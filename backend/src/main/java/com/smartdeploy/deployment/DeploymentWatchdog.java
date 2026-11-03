package com.smartdeploy.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Garde-fou : passe régulièrement en revue les déploiements en attente
 * ou en cours, et marque en échec ceux qui dépassent leur délai, pour
 * qu'un environnement ne reste jamais verrouillé.
 */
@Component
public class DeploymentWatchdog {

    private static final Logger log = LoggerFactory.getLogger(DeploymentWatchdog.class);

    private final DeploymentService deploymentService;
    private final Duration pendingTimeout;
    private final Duration runningTimeout;

    public DeploymentWatchdog(
            DeploymentService deploymentService,
            @Value("${smartdeploy.deployments.pending-timeout-minutes:30}") long pendingMinutes,
            @Value("${smartdeploy.deployments.running-timeout-minutes:120}") long runningMinutes
    ) {
        this.deploymentService = deploymentService;
        this.pendingTimeout = Duration.ofMinutes(pendingMinutes);
        this.runningTimeout = Duration.ofMinutes(runningMinutes);
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void expireStaleDeployments() {
        try {
            int expired = deploymentService.expireStaleDeployments(
                    LocalDateTime.now(), pendingTimeout, runningTimeout
            );
            if (expired > 0) {
                log.warn("Garde-fou : {} déploiement(s) bloqué(s) marqué(s) en échec", expired);
            }
        } catch (RuntimeException exception) {
            // Une erreur ponctuelle ne doit pas arrêter le planificateur.
            log.warn("Garde-fou : vérification impossible ({})", exception.getMessage());
        }
    }
}
