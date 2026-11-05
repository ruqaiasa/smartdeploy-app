package com.smartdeploy.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Règles du gardien, sans dépendance : à partir d'une mesure, produit des
 * contrôles explicables. Les seuils sont volontairement simples et justifiés,
 * pour qu'un humain puisse vérifier chaque décision.
 */
public final class GuardEvaluator {

    /** Redémarrages tolérés avant d'être jugés critiques. */
    static final int CRITICAL_RESTARTS = 2;
    /** Utilisation de la limite mémoire au-delà de laquelle on avertit. */
    static final double MEMORY_LIMIT_WARNING = 0.90;
    /** CPU : doublement et au moins 20 points de plus qu'avant le déploiement. */
    static final double CPU_FACTOR = 2.0;
    static final double CPU_MIN_INCREASE = 20.0;
    /** Mémoire : +50 % et au moins 100 Mo de plus qu'avant le déploiement. */
    static final double MEMORY_FACTOR = 1.5;
    static final double MEMORY_MIN_INCREASE_MB = 100.0;

    /**
     * Période de démarrage : la JVM et les caches consomment beaucoup de
     * processeur et de mémoire juste après la mise en service, d'autant plus
     * que le conteneur est limité à un cœur. Le CPU étant mesuré en moyenne
     * sur une fenêtre glissante de 2 minutes, ce pic reste visible environ
     * 2 minutes après sa fin : 3 minutes de démarrage + 2 minutes de fenêtre.
     * Les contrôles critiques, eux, restent actifs dès la première seconde.
     */
    static final long WARMUP_SECONDS = 300;

    private GuardEvaluator() {
    }

    public static GuardEvaluation evaluate(GuardSnapshot snapshot) {
        return evaluate(snapshot, Long.MAX_VALUE);
    }

    public static GuardEvaluation evaluate(GuardSnapshot snapshot, long secondsSinceStart) {
        if (snapshot == null) {
            GuardCheck unavailable = new GuardCheck("prometheus", "Lecture des métriques", GuardLevel.UNKNOWN,
                    "Prometheus injoignable", null, "Aucune mesure possible pour cette évaluation.");
            return new GuardEvaluation(GuardLevel.UNKNOWN, List.of(unavailable));
        }

        List<GuardCheck> checks = new ArrayList<>();
        checks.add(availability(snapshot));
        checks.add(blockedContainers(snapshot));
        checks.add(restarts(snapshot));
        checks.add(outOfMemory(snapshot));
        checks.add(memoryLimit(snapshot));
        if (secondsSinceStart < WARMUP_SECONDS) {
            checks.add(warmingUp("cpu_drift", "CPU par rapport à avant le déploiement",
                    snapshot.cpuPercent() == null ? "non mesuré" : percent(snapshot.cpuPercent()), secondsSinceStart));
            checks.add(warmingUp("memory_drift", "Mémoire par rapport à avant le déploiement",
                    snapshot.memoryMb() == null ? "non mesurée" : megabytes(snapshot.memoryMb()), secondsSinceStart));
        } else {
            checks.add(cpuDrift(snapshot));
            checks.add(memoryDrift(snapshot));
        }

        GuardLevel level = checks.stream().map(GuardCheck::level)
                .filter(l -> l == GuardLevel.CRITICAL || l == GuardLevel.WARNING)
                .max(Enum::compareTo)
                .orElse(GuardLevel.OK);
        return new GuardEvaluation(level, List.copyOf(checks));
    }

    private static GuardCheck warmingUp(String key, String label, String observed, long secondsSinceStart) {
        long remaining = Math.max(0, WARMUP_SECONDS - secondsSinceStart);
        return new GuardCheck(key, label, GuardLevel.UNKNOWN, observed, "démarrage en cours",
                "Période de démarrage : dérive non évaluée pendant les 5 premières minutes (encore "
                        + remaining + " s).");
    }

    private static GuardCheck availability(GuardSnapshot s) {
        if (s.unavailablePods() == null || s.desiredPods() == null) {
            return new GuardCheck("availability", "Disponibilité des pods", GuardLevel.UNKNOWN,
                    "non mesurée", null, "Les métriques Kubernetes ne sont pas disponibles.");
        }
        if (s.unavailablePods() > 0) {
            return new GuardCheck("availability", "Disponibilité des pods", GuardLevel.CRITICAL,
                    s.unavailablePods() + " pod(s) indisponible(s)", s.desiredPods() + " pod(s) attendu(s)",
                    "Une partie de l'application ne répond plus aux sondes de Kubernetes.");
        }
        return new GuardCheck("availability", "Disponibilité des pods", GuardLevel.OK,
                "tous disponibles", s.desiredPods() + " pod(s) attendu(s)", "Tous les pods sont prêts.");
    }

    private static GuardCheck blockedContainers(GuardSnapshot s) {
        if (!s.blockedContainers().isEmpty()) {
            return new GuardCheck("blocked", "Conteneurs bloqués", GuardLevel.CRITICAL,
                    String.join(", ", s.blockedContainers()), "aucun",
                    "Un conteneur ne parvient pas à démarrer ou redémarre en boucle.");
        }
        return new GuardCheck("blocked", "Conteneurs bloqués", GuardLevel.OK, "aucun", "aucun",
                "Aucun conteneur en attente anormale.");
    }

    private static GuardCheck restarts(GuardSnapshot s) {
        if (s.restarts() == null) {
            return new GuardCheck("restarts", "Redémarrages depuis le déploiement", GuardLevel.UNKNOWN,
                    "non mesurés", null, "Le compteur de redémarrages n'est pas disponible.");
        }
        int restarts = s.restarts();
        GuardLevel level = restarts >= CRITICAL_RESTARTS ? GuardLevel.CRITICAL
                : restarts == 1 ? GuardLevel.WARNING : GuardLevel.OK;
        String explanation = switch (level) {
            case CRITICAL -> "Des redémarrages répétés indiquent une application instable.";
            case WARNING -> "Un redémarrage isolé : à surveiller.";
            default -> "Aucun redémarrage depuis la mise en service.";
        };
        return new GuardCheck("restarts", "Redémarrages depuis le déploiement", level,
                String.valueOf(restarts), "moins de " + CRITICAL_RESTARTS, explanation);
    }

    private static GuardCheck outOfMemory(GuardSnapshot s) {
        boolean restarted = s.restarts() != null && s.restarts() > 0;
        if (!s.oomKilledPods().isEmpty() && restarted) {
            return new GuardCheck("oom", "Arrêt pour mémoire insuffisante", GuardLevel.CRITICAL,
                    "OOMKilled : " + String.join(", ", s.oomKilledPods()), "aucun",
                    "Le conteneur a dépassé sa limite mémoire et a été arrêté par Kubernetes.");
        }
        return new GuardCheck("oom", "Arrêt pour mémoire insuffisante", GuardLevel.OK, "aucun", "aucun",
                "Aucun conteneur arrêté pour mémoire insuffisante.");
    }

    private static GuardCheck memoryLimit(GuardSnapshot s) {
        if (s.memoryLimitRatio() == null) {
            return new GuardCheck("memory_limit", "Mémoire par rapport à la limite", GuardLevel.UNKNOWN,
                    "non mesurée", null, "Aucune limite mémoire déclarée ou mesure indisponible.");
        }
        String observed = percent(s.memoryLimitRatio() * 100);
        String reference = "moins de " + percent(MEMORY_LIMIT_WARNING * 100);
        if (s.memoryLimitRatio() >= MEMORY_LIMIT_WARNING) {
            return new GuardCheck("memory_limit", "Mémoire par rapport à la limite", GuardLevel.WARNING,
                    observed, reference, "Le conteneur approche de sa limite : risque d'arrêt pour mémoire insuffisante.");
        }
        return new GuardCheck("memory_limit", "Mémoire par rapport à la limite", GuardLevel.OK,
                observed, reference, "Marge mémoire suffisante.");
    }

    private static GuardCheck cpuDrift(GuardSnapshot s) {
        if (s.cpuPercent() == null) {
            return new GuardCheck("cpu_drift", "CPU par rapport à avant le déploiement", GuardLevel.UNKNOWN,
                    "non mesuré", null, "La consommation CPU n'est pas disponible.");
        }
        if (s.baselineCpuPercent() == null) {
            return new GuardCheck("cpu_drift", "CPU par rapport à avant le déploiement", GuardLevel.UNKNOWN,
                    percent(s.cpuPercent()), "pas de référence",
                    "Aucune mesure avant le déploiement (premier déploiement ou métriques absentes).");
        }
        double now = s.cpuPercent(), before = s.baselineCpuPercent();
        boolean drift = now > before * CPU_FACTOR && now - before >= CPU_MIN_INCREASE;
        return new GuardCheck("cpu_drift", "CPU par rapport à avant le déploiement",
                drift ? GuardLevel.WARNING : GuardLevel.OK, percent(now), "avant : " + percent(before),
                drift ? "La consommation CPU a plus que doublé depuis le déploiement."
                        : "Consommation CPU comparable à celle d'avant le déploiement.");
    }

    private static GuardCheck memoryDrift(GuardSnapshot s) {
        if (s.memoryMb() == null) {
            return new GuardCheck("memory_drift", "Mémoire par rapport à avant le déploiement", GuardLevel.UNKNOWN,
                    "non mesurée", null, "La consommation mémoire n'est pas disponible.");
        }
        if (s.baselineMemoryMb() == null) {
            return new GuardCheck("memory_drift", "Mémoire par rapport à avant le déploiement", GuardLevel.UNKNOWN,
                    megabytes(s.memoryMb()), "pas de référence",
                    "Aucune mesure avant le déploiement (premier déploiement ou métriques absentes).");
        }
        double now = s.memoryMb(), before = s.baselineMemoryMb();
        boolean drift = now > before * MEMORY_FACTOR && now - before >= MEMORY_MIN_INCREASE_MB;
        return new GuardCheck("memory_drift", "Mémoire par rapport à avant le déploiement",
                drift ? GuardLevel.WARNING : GuardLevel.OK, megabytes(now), "avant : " + megabytes(before),
                drift ? "La mémoire a augmenté de plus de 50 % depuis le déploiement : fuite possible."
                        : "Consommation mémoire comparable à celle d'avant le déploiement.");
    }

    static String percent(double value) {
        return String.format(Locale.FRANCE, "%.0f %%", value);
    }

    static String megabytes(double value) {
        return String.format(Locale.FRANCE, "%.0f Mo", value);
    }
}
