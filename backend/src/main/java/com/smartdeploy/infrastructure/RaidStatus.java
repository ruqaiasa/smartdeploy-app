package com.smartdeploy.infrastructure;

/** État d'un RAID logiciel de la machine qui héberge le cluster. */
public enum RaidStatus {
    /** Tous les disques prévus sont actifs. */
    HEALTHY,
    /** Un disque au moins manque ou est en panne : plus aucune protection. */
    DEGRADED,
    /** Un disque est en cours de reconstruction. */
    RECOVERING,
    /** Prometheus ne fournit aucune donnée sur ce RAID. */
    UNKNOWN
}
