package com.smartdeploy.guard;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditRecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Démarre la surveillance à chaque déploiement réussi (événement du journal),
 * puis évalue périodiquement les déploiements surveillés.
 */
@Component
public class DeploymentGuardScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeploymentGuardScheduler.class);

    private final DeploymentGuardService guardService;

    public DeploymentGuardScheduler(DeploymentGuardService guardService) {
        this.guardService = guardService;
    }

    @EventListener
    public void onAuditRecorded(AuditRecordedEvent event) {
        var entry = event.entry();
        if (entry.getAction() == AuditAction.DEPLOYMENT_SUCCEEDED && entry.getTargetId() != null) {
            try {
                guardService.start(entry.getTargetId());
            } catch (RuntimeException exception) {
                log.error("Gardien : démarrage impossible pour le déploiement #{}", entry.getTargetId(), exception);
            }
        }
    }

    @Scheduled(
            initialDelayString = "${smartdeploy.guard.initial-delay-ms:20000}",
            fixedDelayString = "${smartdeploy.guard.interval-ms:30000}"
    )
    public void evaluate() {
        guardService.evaluateAll();
    }
}
