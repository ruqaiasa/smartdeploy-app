package com.smartdeploy.backup;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
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

    public BackupTasks(VeleroClient velero, VeleroProperties properties, ApplicationEventPublisher eventPublisher) {
        this.velero = velero;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
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
                           Long environmentId, String environmentName, String applicationName) {
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
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            log.error("Restauration impossible : {}", restoreName, exception);
            audit(AuditAction.RESTORE_FAILED, environmentId, environmentName, applicationName,
                    "Restauration « " + restoreName + " » impossible : " + exception.getMessage(), null);
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
