package com.smartdeploy.infrastructure;

import com.smartdeploy.audit.AuditAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FilesystemEvaluatorTest {

    private static final long GIB = 1024L * 1024 * 1024;

    @Test
    void levelsFollowTheThresholds() {
        Instant now = Instant.now();
        assertEquals(FilesystemUsage.Level.OK, FilesystemEvaluator.evaluate("/dev/md0", "/srv/raid", 10 * GIB, 5 * GIB, now).level());
        assertEquals(FilesystemUsage.Level.WARNING, FilesystemEvaluator.evaluate("/dev/md0", "/srv/raid", 10 * GIB, 15 * GIB / 10, now).level());
        FilesystemUsage full = FilesystemEvaluator.evaluate("/dev/md0", "/srv/raid", 10 * GIB, GIB / 2, now);
        assertEquals(FilesystemUsage.Level.CRITICAL, full.level());
        assertTrue(full.message().contains("95"), full.message());
    }

    @Test
    void oneAlertPerChangeOfLevel() {
        assertNull(FilesystemEvaluator.transition(null, FilesystemUsage.Level.OK), "aucune alerte au démarrage si tout va bien");
        assertEquals(AuditAction.STORAGE_SPACE_LOW, FilesystemEvaluator.transition(FilesystemUsage.Level.OK, FilesystemUsage.Level.WARNING));
        assertEquals(AuditAction.STORAGE_SPACE_CRITICAL, FilesystemEvaluator.transition(FilesystemUsage.Level.WARNING, FilesystemUsage.Level.CRITICAL));
        assertNull(FilesystemEvaluator.transition(FilesystemUsage.Level.CRITICAL, FilesystemUsage.Level.CRITICAL));
        assertNull(FilesystemEvaluator.transition(FilesystemUsage.Level.CRITICAL, FilesystemUsage.Level.WARNING), "pas de fausse « bonne nouvelle »");
        assertEquals(AuditAction.STORAGE_SPACE_OK, FilesystemEvaluator.transition(FilesystemUsage.Level.WARNING, FilesystemUsage.Level.OK));
    }
}
