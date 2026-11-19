package com.smartdeploy.backup;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import com.smartdeploy.deployment.DeploymentService;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Opérations Velero longues, exécutées en arrière-plan : attente de la fin
 * d'une sauvegarde ou d'une restauration, puis traçabilité du résultat.
 */
@Component
public class BackupTasks {

    private static final Logger log = LoggerFactory.getLogger(BackupTasks.class);
    static final long POLL_MILLIS = 5_000;

    private final VeleroClient velero;
    private final VeleroProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final DeploymentService deploymentService;

    public BackupTasks(VeleroClient velero, VeleroProperties properties, ApplicationEventPublisher eventPublisher,
                       DeploymentService deploymentService) {
        this.velero = velero;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.deploymentService = deploymentService;
    }

    @Async
    public void watchBackup(String name, Long environmentId, String environmentName, String applicationName) {
        String phase = waitFor(() -> velero.backup(name), properties.restorePointTimeoutSeconds());
        boolean ok = "Completed".equals(phase);
        audit(ok ? AuditAction.BACKUP_COMPLETED : AuditAction.BACKUP_FAILED, environmentId, environmentName, applicationName,
                "Sauvegarde « " + name + " » : " + phase, ok ? null : "Consulter : velero backup describe " + name + " --details");
    }

    @Async
    public void runRestore(String restoreName, String backupName, String sourceNamespace, String targetNamespace, boolean replace,
                           Long environmentId, String environmentName, String applicationName,
                           java.time.LocalDateTime backupTakenAt, Long restorePointDeploymentId) {
        long startedAt = System.currentTimeMillis();
        try {
            if (replace && velero.namespaceExists(targetNamespace)) {
                velero.deleteNamespace(targetNamespace);
                long deadline = System.currentTimeMillis() + 300_000;
                while (velero.namespaceExists(targetNamespace) && System.currentTimeMillis() < deadline) {
                    Thread.sleep(POLL_MILLIS);
                }
            }
            velero.createRestore(restoreName, backupName, sourceNamespace, targetNamespace,
                    Map.of(BackupNames.LABEL_ENVIRONMENT, String.valueOf(environmentId)));
            String phase = waitFor(() -> velero.restore(restoreName), 1800);
            boolean ok = "Completed".equals(phase);
            audit(ok ? AuditAction.RESTORE_COMPLETED : AuditAction.RESTORE_FAILED, environmentId, environmentName, applicationName,
                    "Restauration « " + restoreName + " » vers " + targetNamespace + " : " + phase,
                    ok ? null : "Consulter : velero restore describe " + restoreName + " --details");
            if (ok && replace) {
                // L'environnement tourne de nouveau dans l'état de la sauvegarde.
                deploymentService.markReplacedAfterRestore(environmentId, backupTakenAt, restorePointDeploymentId, restoreName);
                verifyRestoredWorkloads(restoreName, targetNamespace, startedAt, environmentId, environmentName, applicationName);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            log.error("Restauration impossible : {}", restoreName, exception);
            audit(AuditAction.RESTORE_FAILED, environmentId, environmentName, applicationName,
                    "Restauration « " + restoreName + " » impossible : " + exception.getMessage(), null);
        }
    }

    /**
     * Après une restauration en remplacement, vérifie que l'application redémarre réellement :
     * toutes ses charges doivent être prêtes dans le délai imparti. La durée totale, de la demande
     * à l'application de nouveau prête, est le temps de reprise (RTO) effectivement mesuré.
     */
    void verifyRestoredWorkloads(String restoreName, String namespace, long startedAt,
                                 Long environmentId, String environmentName, String applicationName) throws InterruptedException {
        long deadline = System.currentTimeMillis() + properties.restoreHealthTimeoutSeconds() * 1000L;
        java.util.List<String> unready = velero.unreadyWorkloads(namespace);
        while (!unready.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MILLIS);
            unready = velero.unreadyWorkloads(namespace);
        }
        long seconds = (System.currentTimeMillis() - startedAt) / 1000;
        if (unready.isEmpty() && velero.workloadCount(namespace) > 0) {
            audit(AuditAction.RESTORE_VERIFIED, environmentId, environmentName, applicationName,
                    "Après « " + restoreName + " », l'application est de nouveau prête : temps de reprise mesuré " + seconds + " s",
                    "Toutes les charges de " + namespace + " sont prêtes.");
        } else {
            audit(AuditAction.RESTORE_UNHEALTHY, environmentId, environmentName, applicationName,
                    "Après « " + restoreName + " », l'application n'est pas rétablie au bout de " + seconds + " s",
                    unready.isEmpty() ? "Aucune charge trouvée dans " + namespace : "Non prêtes : " + String.join(", ", unready));
        }
    }

    /** Attend qu'un objet Velero atteigne une phase finale ; renvoie la phase ou « Timeout ». */
    String waitFor(Supplier<Optional<GenericKubernetesResource>> resource, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        try {
            while (System.currentTimeMillis() < deadline) {
                String phase = resource.get().map(VeleroClient::phase).orElse("New");
                if (VeleroClient.finished(phase)) {
                    return phase;
                }
                Thread.sleep(POLL_MILLIS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return "Timeout";
    }

    private void audit(AuditAction action, Long environmentId, String environmentName, String applicationName,
                       String summary, String details) {
        eventPublisher.publishEvent(new AuditEvent(action, BackupService.ACTOR, "ENVIRONMENT", environmentId,
                applicationName, environmentName, summary, details, false));
    }
}
