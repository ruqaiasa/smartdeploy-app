package com.smartdeploy.infrastructure;

import java.time.Instant;

/**
 * L'état d'un RAID à un instant donné, tel que lu dans Prometheus
 * (métriques node_md_* publiées par node_exporter).
 */
public record RaidSnapshot(
        String device,
        RaidStatus status,
        Integer requiredDisks,
        Integer activeDisks,
        Integer failedDisks,
        boolean recovering,
        String message,
        Instant checkedAt
) {
}
