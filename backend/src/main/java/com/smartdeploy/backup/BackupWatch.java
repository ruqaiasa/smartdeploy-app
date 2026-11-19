package com.smartdeploy.backup;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Surveillance de la protection des données : âge et résultat des sauvegardes de chaque
 * environnement, et disponibilité du stockage des sauvegardes (Azure). Comme pour le RAID,
 * chaque changement d'état est inscrit au journal, donc notifié, une seule fois.
 * Seul le passage à l'état critique est notifié : un environnement neuf, qui attend sa
 * première sauvegarde de la nuit, reste en simple avertissement sur le tableau de bord.
 */
@Component
public class BackupWatch {

    private static final Logger log = LoggerFactory.getLogger(BackupWatch.class);
    static final String ACTOR = "Surveillance des sauvegardes";

    public record StorageLocationStatus(String phase, boolean readable, Instant checkedAt) {
    }

    private final BackupService backupService;
    private final VeleroClient velero;
    private final VeleroProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<Long, BackupProtection.Level> lastLevels = new ConcurrentHashMap<>();
    private volatile StorageLocationStatus storageLocation = new StorageLocationStatus(null, false, null);
    private volatile boolean storageWarningLogged;

    public BackupWatch(BackupService backupService, VeleroClient velero, VeleroProperties properties,
                       ApplicationEventPublisher eventPublisher) {
        this.backupService = backupService;
        this.velero = velero;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(initialDelayString = "${smartdeploy.velero.watch-initial-delay-ms:120000}",
               fixedDelayString = "${smartdeploy.velero.watch-delay-ms:900000}")
    public void check() {
        if (!properties.enabled()) {
            return;
        }
        checkStorageLocation();
        checkProtection();
    }

    void checkStorageLocation() {
        String previous = storageLocation.phase();
        try {
            String phase = velero.storageLocationPhase().orElse(null);
            storageLocation = new StorageLocationStatus(phase, phase != null, Instant.now());
            storageWarningLogged = false;
            AuditAction action = storageTransition(previous, phase);
            if (action != null) {
                eventPublisher.publishEvent(new AuditEvent(action, ACTOR, "INFRASTRUCTURE", null, null, null,
                        action == AuditAction.BACKUP_STORAGE_UNAVAILABLE
                                ? "Le stockage des sauvegardes est indisponible : aucune sauvegarde ne peut aboutir"
                                : "Le stockage des sauvegardes est de nouveau disponible",
                        "Emplacement Velero « default » : " + (previous == null ? "état inconnu" : previous) + " → " + phase, false));
            }
        } catch (RuntimeException exception) {
            storageLocation = new StorageLocationStatus(previous, false, Instant.now());
            if (!storageWarningLogged) {
                log.warn("Surveillance des sauvegardes : état du stockage illisible ({}). Le compte de service de SmartDeploy "
                        + "doit pouvoir lire les objets backupstoragelocations de l'espace velero.", exception.getMessage());
                storageWarningLogged = true;
            }
        }
    }

    void checkProtection() {
        try {
            for (BackupProtection.EnvironmentProtection protection : backupService.protection()) {
                BackupProtection.Level previous = lastLevels.put(protection.environmentId(), protection.level());
                AuditAction action = protectionTransition(previous, protection.level());
                if (action != null) {
                    eventPublisher.publishEvent(new AuditEvent(action, ACTOR, "ENVIRONMENT", protection.environmentId(),
                            protection.applicationName(), protection.environmentName(),
                            (action == AuditAction.BACKUP_PROTECTION_DEGRADED ? "Protection dégradée pour " : "Protection rétablie pour ")
                                    + protection.environmentName() + " : " + protection.message(),
                            null, false));
                }
            }
        } catch (RuntimeException exception) {
            log.debug("Surveillance des sauvegardes : protection illisible ({})", exception.getMessage());
        }
    }

    /** Notifie le passage à l'état critique, et le retour à la normale après un état critique. */
    static AuditAction protectionTransition(BackupProtection.Level previous, BackupProtection.Level current) {
        if (current == BackupProtection.Level.CRITICAL && previous != BackupProtection.Level.CRITICAL) {
            return AuditAction.BACKUP_PROTECTION_DEGRADED;
        }
        if (current == BackupProtection.Level.OK && previous == BackupProtection.Level.CRITICAL) {
            return AuditAction.BACKUP_PROTECTION_RESTORED;
        }
        return null;
    }

    /** Notifie la perte du stockage (y compris au premier état observé) et son retour. */
    static AuditAction storageTransition(String previous, String current) {
        if (current == null) {
            return null;
        }
        boolean wasAvailable = "Available".equals(previous);
        boolean isAvailable = "Available".equals(current);
        if (!isAvailable && (previous == null || wasAvailable)) {
            return AuditAction.BACKUP_STORAGE_UNAVAILABLE;
        }
        if (isAvailable && previous != null && !wasAvailable) {
            return AuditAction.BACKUP_STORAGE_AVAILABLE;
        }
        return null;
    }

    public StorageLocationStatus storageLocation() {
        return storageLocation;
    }
}
