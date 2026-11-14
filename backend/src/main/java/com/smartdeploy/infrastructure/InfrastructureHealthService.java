package com.smartdeploy.infrastructure;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Santé de l'infrastructure partagée par toutes les applications : l'état des
 * RAID de la machine. Distinct des alertes applicatives, rattachées à un
 * déploiement. Chaque changement d'état est inscrit au journal (donc notifié
 * sur Slack), une seule fois par changement.
 */
@Service
public class InfrastructureHealthService {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureHealthService.class);
    static final String ACTOR = "Santé de l'infrastructure";

    private final RaidMetricsReader reader;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, RaidSnapshot> lastSnapshots = new ConcurrentHashMap<>();
    private volatile boolean prometheusReachable = true;

    public InfrastructureHealthService(RaidMetricsReader reader, ApplicationEventPublisher eventPublisher) {
        this.reader = reader;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(initialDelayString = "${smartdeploy.infrastructure.raid-initial-delay-ms:20000}",
               fixedDelayString = "${smartdeploy.infrastructure.raid-check-delay-ms:30000}")
    public void checkRaid() {
        List<RaidSnapshot> snapshots;
        try {
            snapshots = reader.read(Instant.now());
            if (!prometheusReachable) {
                log.info("Santé de l'infrastructure : Prometheus de nouveau joignable");
            }
            prometheusReachable = true;
        } catch (RuntimeException exception) {
            if (prometheusReachable) {             // un seul avertissement, pas un par cycle
                log.warn("Santé de l'infrastructure : état du RAID illisible ({})", exception.getMessage());
            }
            prometheusReachable = false;
            return;
        }
        for (RaidSnapshot current : snapshots) {
            RaidSnapshot previous = lastSnapshots.put(current.device(), current);
            AuditAction action = RaidEvaluator.transition(previous == null ? null : previous.status(), current.status());
            if (action != null) {
                eventPublisher.publishEvent(new AuditEvent(action, ACTOR, "INFRASTRUCTURE", null, null, null,
                        "RAID " + current.device() + " : " + current.message(),
                        previous == null ? "Premier état observé" : "Avant : " + previous.status() + " → après : " + current.status(),
                        false));
                log.warn("RAID {} : {}", current.device(), current.message());
            }
        }
    }

    /** Dernier état connu de chaque RAID ; liste vide si aucune mesure encore lue. */
    public List<RaidSnapshot> storage() {
        return lastSnapshots.values().stream()
                .sorted((a, b) -> a.device().compareTo(b.device()))
                .toList();
    }

    public boolean prometheusReachable() {
        return prometheusReachable;
    }
}
