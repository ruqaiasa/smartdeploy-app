package com.smartdeploy.backup;

import com.smartdeploy.audit.AuditAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}
