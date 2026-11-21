package com.smartdeploy.backup;

import com.smartdeploy.audit.AuditAction;
import org.junit.jupiter.api.Test;

import com.smartdeploy.audit.AuditLog;
import com.smartdeploy.audit.AuditLogRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BackupWatchTest {

    @Test
    void onlyTheCriticalStateIsNotified() {
        assertNull(BackupWatch.protectionTransition(null, BackupProtection.Level.WARNING), "environnement neuf : pas d'alerte");
        assertEquals(AuditAction.BACKUP_PROTECTION_DEGRADED, BackupWatch.protectionTransition(BackupProtection.Level.OK, BackupProtection.Level.CRITICAL));
        assertEquals(AuditAction.BACKUP_PROTECTION_DEGRADED, BackupWatch.protectionTransition(null, BackupProtection.Level.CRITICAL));
        assertNull(BackupWatch.protectionTransition(BackupProtection.Level.CRITICAL, BackupProtection.Level.CRITICAL), "une seule alerte");
        assertEquals(AuditAction.BACKUP_PROTECTION_RESTORED, BackupWatch.protectionTransition(BackupProtection.Level.CRITICAL, BackupProtection.Level.OK));
        assertNull(BackupWatch.protectionTransition(BackupProtection.Level.WARNING, BackupProtection.Level.OK));
    }

    @Test
    void storageLossAndReturnAreNotifiedOnce() {
        assertNull(BackupWatch.storageTransition(null, "Available"));
        assertEquals(AuditAction.BACKUP_STORAGE_UNAVAILABLE, BackupWatch.storageTransition(null, "Unavailable"));
        assertEquals(AuditAction.BACKUP_STORAGE_UNAVAILABLE, BackupWatch.storageTransition("Available", "Unavailable"));
        assertNull(BackupWatch.storageTransition("Unavailable", "Unavailable"));
        assertEquals(AuditAction.BACKUP_STORAGE_AVAILABLE, BackupWatch.storageTransition("Unavailable", "Available"));
        assertNull(BackupWatch.storageTransition("Available", null), "état illisible : on ne conclut rien");
    }

    @Test
    void aCriticalStateAlreadyReportedIsNotRepeatedAfterARestart() {
        BackupWatch watch = new BackupWatch(mock(BackupService.class), mock(VeleroClient.class), mock(VeleroProperties.class),
                mock(org.springframework.context.ApplicationEventPublisher.class));
        AuditLogRepository journal = mock(AuditLogRepository.class);
        AuditLog derniere = mock(AuditLog.class);
        when(derniere.getAction()).thenReturn(AuditAction.BACKUP_PROTECTION_DEGRADED);
        when(journal.findTopByTargetTypeAndTargetIdAndActionInOrderByIdDesc(eq("ENVIRONMENT"), eq(7L), anyCollection()))
                .thenReturn(Optional.of(derniere));
        watch.setAuditLog(journal);

        BackupProtection.Level previous = watch.lastNotified(7L);

        assertEquals(BackupProtection.Level.CRITICAL, previous, "l'état critique est retrouvé dans le journal");
        assertNull(BackupWatch.protectionTransition(previous, BackupProtection.Level.CRITICAL), "donc aucune nouvelle alerte");
    }
}
