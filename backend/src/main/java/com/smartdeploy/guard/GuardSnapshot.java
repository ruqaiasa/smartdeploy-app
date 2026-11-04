package com.smartdeploy.guard;

import java.util.List;

/**
 * Métriques d'une application à un instant donné, lues dans Prometheus.
 * Les valeurs absentes (null) signifient « non mesurable », pas « zéro ».
 *
 * @param desiredPods         pods attendus (Deployments de l'application)
 * @param unavailablePods     pods non disponibles (Deployments et base)
 * @param restarts            redémarrages depuis le début de la surveillance
 * @param blockedContainers   conteneurs bloqués, sous la forme « pod : raison »
 * @param oomKilledPods       pods arrêtés pour mémoire insuffisante
 * @param memoryLimitRatio    plus forte utilisation mémoire / limite (0 à 1)
 * @param cpuPercent          CPU actuel, en % d'un cœur
 * @param memoryMb            mémoire actuelle, en Mo
 * @param baselineCpuPercent  CPU moyen avant le déploiement
 * @param baselineMemoryMb    mémoire moyenne avant le déploiement
 */
public record GuardSnapshot(
        Integer desiredPods,
        Integer unavailablePods,
        Integer restarts,
        List<String> blockedContainers,
        List<String> oomKilledPods,
        Double memoryLimitRatio,
        Double cpuPercent,
        Double memoryMb,
        Double baselineCpuPercent,
        Double baselineMemoryMb
) {
    public GuardSnapshot {
        blockedContainers = blockedContainers == null ? List.of() : List.copyOf(blockedContainers);
        oomKilledPods = oomKilledPods == null ? List.of() : List.copyOf(oomKilledPods);
    }
}
