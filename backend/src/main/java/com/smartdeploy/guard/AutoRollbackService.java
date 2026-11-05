package com.smartdeploy.guard;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import com.smartdeploy.audit.AuditRecordedEvent;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentResponse;
import com.smartdeploy.deployment.DeploymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retour arrière automatique : sur les environnements autorisés
 * (développement et test par défaut), une dégradation confirmée par le
 * gardien déclenche le retour à la dernière version saine, sans attendre
 * l'humain. Ailleurs, le retour reste seulement proposé.
 *
 * Le retour passe par la même méthode qu'un retour manuel, avec toutes ses
 * vérifications. Il n'y a jamais de retour automatique d'un retour arrière
 * (le gardien ne propose pas de cible dans ce cas), ce qui exclut les boucles.
 */
@Component
public class AutoRollbackService {

    private static final Logger log = LoggerFactory.getLogger(AutoRollbackService.class);

    private final DeploymentGuardRepository guardRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentService deploymentService;
    private final GuardProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactions;

    @Autowired
    public AutoRollbackService(DeploymentGuardRepository guardRepository, DeploymentRepository deploymentRepository,
                               DeploymentService deploymentService, GuardProperties properties,
                               ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {
        this(guardRepository, deploymentRepository, deploymentService, properties, eventPublisher, requiresNew(transactionManager));
    }

    AutoRollbackService(DeploymentGuardRepository guardRepository, DeploymentRepository deploymentRepository,
                        DeploymentService deploymentService, GuardProperties properties,
                        ApplicationEventPublisher eventPublisher, TransactionOperations transactions) {
        this.guardRepository = guardRepository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentService = deploymentService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
    }

    private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @EventListener
    public void onAuditRecorded(AuditRecordedEvent event) {
        if (event.entry().getAction() == AuditAction.GUARD_DEGRADED && event.entry().getTargetId() != null) {
            rollBackIfAllowed(event.entry().getTargetId());
        }
    }

    void rollBackIfAllowed(Long degradedDeploymentId) {
        try {
            transactions.executeWithoutResult(status -> {
                Deployment degraded = deploymentRepository.findByIdAndDeletedFalse(degradedDeploymentId).orElse(null);
                if (degraded == null || !properties.autoRollback(degraded.getEnvironment().getType())) {
                    return;
                }
                DeploymentGuard guard = guardRepository.findFirstByDeploymentIdOrderByIdDesc(degradedDeploymentId).orElse(null);
                if (guard == null || guard.getStatus() != GuardStatus.DEGRADED || guard.getRollbackTargetId() == null
                        || guard.getAutoRollbackDeploymentId() != null) {
                    return;
                }
                DeploymentResponse rollback = deploymentService.createRollback(guard.getRollbackTargetId(), degraded.getRequestedBy());
                guard.setAutoRollbackDeploymentId(rollback.id());
                audit(degraded, "Retour arrière automatique lancé : déploiement #" + rollback.id()
                        + " vers le déploiement sain #" + guard.getRollbackTargetId(),
                        "Environnement " + degraded.getEnvironment().getType()
                                + " : retour automatique autorisé après une dégradation confirmée.");
                log.info("Gardien : retour arrière automatique #{} pour le déploiement dégradé #{}", rollback.id(), degradedDeploymentId);
            });
        } catch (RuntimeException exception) {
            // Le retour reste proposé dans l'interface ; la raison du refus est tracée.
            log.warn("Gardien : retour arrière automatique impossible pour #{} : {}", degradedDeploymentId, exception.getMessage());
            transactions.executeWithoutResult(status -> deploymentRepository.findByIdAndDeletedFalse(degradedDeploymentId)
                    .ifPresent(degraded -> audit(degraded, "Retour arrière automatique impossible : " + exception.getMessage(),
                            "Le retour reste proposé dans l'interface.")));
        }
    }

    private void audit(Deployment degraded, String summary, String details) {
        eventPublisher.publishEvent(new AuditEvent(AuditAction.GUARD_AUTO_ROLLBACK, DeploymentGuardService.GUARD_ACTOR,
                "DEPLOYMENT", degraded.getId(), degraded.getVersion().getApplication().getName(),
                degraded.getEnvironment().getName(), summary, details, false));
    }
}
