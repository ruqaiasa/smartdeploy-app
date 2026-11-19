package com.smartdeploy.infrastructure;

import java.time.Instant;

/**
 * Occupation du système de fichiers posé sur un RAID (métriques node_filesystem_* de node_exporter).
 *
 * @param level OK sous 80 %, WARNING de 80 à 90 %, CRITICAL au-delà
 */
public record FilesystemUsage(
        String device,
        String mountpoint,
        long sizeBytes,
        long availableBytes,
        double usedPercent,
        Level level,
        String message,
        Instant checkedAt
) {
    public enum Level { OK, WARNING, CRITICAL }
}
