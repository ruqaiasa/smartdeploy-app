package com.smartdeploy.infrastructure;

import com.smartdeploy.audit.AuditAction;

import java.time.Instant;
import java.util.Locale;

/** Décisions pures sur l'espace disque du RAID (testables sans Prometheus). */
public final class FilesystemEvaluator {

    static final double WARNING_PERCENT = 80.0;
    static final double CRITICAL_PERCENT = 90.0;

    private FilesystemEvaluator() {
    }

    public static FilesystemUsage evaluate(String device, String mountpoint, long size, long available, Instant now) {
        double used = size <= 0 ? 0 : 100.0 * (size - available) / size;
        FilesystemUsage.Level level = used >= CRITICAL_PERCENT ? FilesystemUsage.Level.CRITICAL
                : used >= WARNING_PERCENT ? FilesystemUsage.Level.WARNING : FilesystemUsage.Level.OK;
        String message = String.format(Locale.FRENCH, "%s occupé à %.0f %% (%s libres sur %s)",
                mountpoint, used, gib(available), gib(size));
        return new FilesystemUsage(device, mountpoint, size, available, used, level, message, now);
    }

    /** Une alerte par changement de niveau ; rien au premier état observé s'il est normal. */
    static AuditAction transition(FilesystemUsage.Level previous, FilesystemUsage.Level current) {
        if (current == previous) {
            return null;
        }
        return switch (current) {
            case CRITICAL -> AuditAction.STORAGE_SPACE_CRITICAL;
            case WARNING -> previous == FilesystemUsage.Level.CRITICAL ? null : AuditAction.STORAGE_SPACE_LOW;
            case OK -> previous == null ? null : AuditAction.STORAGE_SPACE_OK;
        };
    }

    static String gib(long bytes) {
        return String.format(Locale.FRENCH, "%.1f Go", bytes / (1024.0 * 1024 * 1024));
    }
}
