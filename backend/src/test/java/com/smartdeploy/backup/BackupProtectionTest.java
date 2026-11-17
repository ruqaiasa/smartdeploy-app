package com.smartdeploy.backup;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackupProtectionTest {

    private static final Instant NOW = Instant.parse("2026-11-16T20:00:00Z");
    private static final BackupEnvironment ENV =
            new BackupEnvironment(5L, "fullstack-demo", "fullstack-demo-test", "sd-test-fullstack-demo", true);

    private static BackupInfo backup(String name, String phase, String started, String completed) {
        return new BackupInfo(name, "SCHEDULED", null, null, phase, started, completed, null, 47, 0, 0);
    }

    @Test
    void recentNightlyBackupMeansProtected() {
        var p = BackupProtection.evaluate(ENV, List.of(
                backup("nuit", "Completed", "2026-11-16T01:00:00Z", "2026-11-16T01:01:25Z")), NOW);
        assertEquals(BackupProtection.Level.OK, p.level());
        assertEquals("Protégé : dernière sauvegarde il y a 18 h", p.message());
    }

    @Test
    void oldOrFailedBackupsAreFlagged() {
        assertEquals(BackupProtection.Level.WARNING, BackupProtection.evaluate(ENV, List.of(
                backup("vieille", "Completed", "2026-11-15T10:00:00Z", "2026-11-15T10:01:00Z")), NOW).level(), "34 h");
        assertEquals(BackupProtection.Level.CRITICAL, BackupProtection.evaluate(ENV, List.of(
                backup("tres-vieille", "Completed", "2026-11-13T10:00:00Z", "2026-11-13T10:01:00Z")), NOW).level(), "82 h");
        assertEquals(BackupProtection.Level.CRITICAL, BackupProtection.evaluate(ENV, List.of(
                backup("ok", "Completed", "2026-11-16T01:00:00Z", "2026-11-16T01:01:00Z"),
                backup("ratee", "Failed", "2026-11-16T12:00:00Z", null)), NOW).level(),
                "la dernière tentative a échoué, même si une sauvegarde récente existe");
        assertEquals(BackupProtection.Level.WARNING, BackupProtection.evaluate(ENV, List.of(), NOW).level(),
                "environnement neuf : pas encore de sauvegarde, mais la nuit la fera");
    }

    @Test
    void labelValuesAreKubernetesCompliant() {
        assertEquals("1.1.1-plantage", BackupNames.labelValue("1.1.1-plantage"));
        assertEquals("version-2-beta", BackupNames.labelValue("version 2 (beta)"));
        assertNull(BackupNames.labelValue("  "));
    }
}
