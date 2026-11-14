package com.smartdeploy.infrastructure;

import com.smartdeploy.audit.AuditAction;

import java.time.Instant;

/**
 * Décision pure sur l'état d'un RAID, et sur l'événement à publier lors d'un
 * changement d'état. Sans Spring ni réseau : testable isolément.
 */
public final class RaidEvaluator {

    private RaidEvaluator() {
    }

    public static RaidSnapshot evaluate(String device, Integer required, Integer active, Integer failed,
                                        boolean recovering, Instant now) {
        if (required == null || active == null) {
            return new RaidSnapshot(device, RaidStatus.UNKNOWN, required, active, failed, false,
                    "Aucune mesure du RAID dans Prometheus (node_exporter injoignable ?)", now);
        }
        int failedDisks = failed == null ? 0 : failed;
        if (recovering) {
            return new RaidSnapshot(device, RaidStatus.RECOVERING, required, active, failedDisks, true,
                    "Reconstruction en cours : " + active + " disque(s) à jour sur " + required, now);
        }
        if (active < required || failedDisks > 0) {
            return new RaidSnapshot(device, RaidStatus.DEGRADED, required, active, failedDisks, false,
                    "RAID dégradé : " + active + " disque(s) actif(s) sur " + required
                            + (failedDisks > 0 ? ", " + failedDisks + " en panne" : "")
                            + ". Les données n'ont plus de copie : remplacer le disque.", now);
        }
        return new RaidSnapshot(device, RaidStatus.HEALTHY, required, active, failedDisks, false,
                "RAID sain : " + active + " disque(s) actif(s) sur " + required, now);
    }

    /**
     * Événement à publier lors du passage de {@code previous} à {@code current}, ou null.
     * Un état inconnu ne produit rien (absence de mesure, pas une panne) ;
     * le premier état observé n'est signalé que s'il est anormal.
     */
    public static AuditAction transition(RaidStatus previous, RaidStatus current) {
        if (current == previous || current == RaidStatus.UNKNOWN) {
            return null;
        }
        return switch (current) {
            case DEGRADED -> AuditAction.RAID_DEGRADED;
            case RECOVERING -> AuditAction.RAID_RECOVERING;
            case HEALTHY -> previous == RaidStatus.DEGRADED || previous == RaidStatus.RECOVERING
                    ? AuditAction.RAID_RESTORED : null;
            case UNKNOWN -> null;
        };
    }
}
