package com.smartdeploy.backup;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import com.smartdeploy.deployment.DeploymentPipelineLauncher;
import com.smartdeploy.deployment.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Point de restauration avant chaque déploiement : sauvegarde Velero de
 * l'environnement (ressources et données), attente de sa fin, puis lancement
 * du pipeline. Associé au retour arrière par empreinte (le code), il permet un
 * retour cohérent de l'application et de ses données.
 * Si la sauvegarde échoue, le déploiement est tout de même lancé et l'échec est tracé.
 */
@Component
public class RestorePointCoordinator {

    private static final Logger log = LoggerFactory.getLogger(RestorePointCoordinator.class);

    private record Target(Long deploymentId, Long environmentId, String environmentName, String namespace, String applicationName) {
    }

    private final DeploymentRepository deploymentRepository;
    private final DeploymentPipelineLauncher launcher;
    private final VeleroClient velero;
    private final VeleroProperties properties;
    private final BackupTasks tasks;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate readTransaction;

    public RestorePointCoordinator(DeploymentRepository deploymentRepository, DeploymentPipelineLauncher launcher,
                                   VeleroClient velero, VeleroProperties properties, BackupTasks tasks,
                                   ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {
        this.deploymentRepository = deploymentRepository;
        this.launcher = launcher;
        this.velero = velero;
        this.properties = properties;
        this.tasks = tasks;
        this.eventPublisher = eventPublisher;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Async
    @EventListener
    public void onRestorePointRequested(RestorePointRequestedEvent event) {
        Target target = readTransaction.execute(status -> deploymentRepository.findByIdAndDeletedFalse(event.deploymentId())
                .map(d -> new Target(d.getId(), d.getEnvironment().getId(), d.getEnvironment().getName(),
                        d.getEnvironment().getNamespace(), d.getVersion().getApplication().getName()))
                .orElse(null));
        if (target != null) {
            createRestorePoint(target);
        }
        launcher.launchAfterRestorePoint(event.deploymentId());
    }

    private void createRestorePoint(Target target) {
        String name = BackupNames.name("avant-deploiement-" + target.deploymentId(), target.environmentName(), LocalDateTime.now());
        String phase;
        try {
            velero.createBackup(name, target.namespace(), properties.restorePointTtl(), Map.of(
                    BackupNames.LABEL_REASON, "pre-deployment",
                    BackupNames.LABEL_ENVIRONMENT, String.valueOf(target.environmentId()),
                    BackupNames.LABEL_DEPLOYMENT, String.valueOf(target.deploymentId())));
            phase = tasks.waitFor(() -> velero.backup(name), properties.restorePointTimeoutSeconds());
        } catch (RuntimeException exception) {
            log.warn("Point de restauration impossible pour le déploiement #{} : {}", target.deploymentId(), exception.getMessage());
            phase = "Erreur : " + exception.getMessage();
        }
        boolean ok = "Completed".equals(phase);
        eventPublisher.publishEvent(new AuditEvent(ok ? AuditAction.BACKUP_COMPLETED : AuditAction.BACKUP_FAILED,
                BackupService.ACTOR, "DEPLOYMENT", target.deploymentId(), target.applicationName(), target.environmentName(),
                "Point de restauration « " + name + " » avant le déploiement #" + target.deploymentId() + " : " + phase,
                ok ? null : "Le déploiement est tout de même lancé ; l'état antérieur reste couvert par la sauvegarde planifiée.",
                false));
    }
}
