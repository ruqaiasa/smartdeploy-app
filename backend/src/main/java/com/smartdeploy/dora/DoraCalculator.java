package com.smartdeploy.dora;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Calcul des quatre indicateurs DORA à partir de l'historique des déploiements.
 *
 * - Un « changement » est un déploiement qui n'est pas un retour arrière.
 * - Un changement en échec a échoué au pipeline, ou a été jugé dégradé par le gardien.
 * - Le rétablissement va de la mise en service d'un déploiement dégradé à la
 *   mise en service suivante réussie sur le même environnement.
 * Les niveaux reprennent les repères indicatifs des rapports DORA
 * (Accelerate State of DevOps), qui varient légèrement selon les années.
 */
public final class DoraCalculator {

    private DoraCalculator() {
    }

    public static List<DoraMetric> compute(List<DeploymentFact> facts, int periodDays) {
        List<DeploymentFact> changes = facts.stream().filter(f -> !f.rollback()).toList();
        List<DeploymentFact> deployed = changes.stream().filter(DeploymentFact::success).toList();
        List<DeploymentFact> finished = changes.stream().filter(f -> f.success() || f.failed()).toList();
        List<DoraMetric> metrics = new ArrayList<>();

        // 1. Fréquence de déploiement
        double perDay = (double) deployed.size() / Math.max(1, periodDays);
        metrics.add(new DoraMetric("deployment_frequency", "Fréquence de déploiement", round(perDay * 7, 1), "par semaine",
                deployed.isEmpty() ? "NONE" : perDay >= 1 ? "ELITE" : perDay >= 1.0 / 7 ? "HIGH" : perDay >= 1.0 / 30 ? "MEDIUM" : "LOW",
                deployed.size() + " mise(s) en service sur " + periodDays + " jours",
                "Nombre de changements mis en service avec succès, ramené à la semaine (retours arrière exclus)."));

        // 2. Délai de livraison (de la demande à la mise en service)
        List<Double> leadMinutes = deployed.stream().filter(f -> f.finishedAt() != null)
                .map(f -> minutes(f.createdAt(), f.finishedAt())).toList();
        Double lead = median(leadMinutes);
        metrics.add(new DoraMetric("lead_time", "Délai de livraison", lead == null ? null : round(lead, 1), "minutes (médiane)",
                lead == null ? "NONE" : lead < 24 * 60 ? "ELITE" : lead < 7 * 24 * 60 ? "HIGH" : lead < 30 * 24 * 60 ? "MEDIUM" : "LOW",
                leadMinutes.size() + " déploiement(s) mesuré(s)",
                "Médiane du temps entre la demande de déploiement et la mise en service. DORA mesure depuis le commit : "
                        + "cette valeur en est la partie automatisée."));

        // 3. Taux d'échec des changements
        long pipelineFailures = finished.stream().filter(DeploymentFact::failed).count();
        long degradations = deployed.stream().filter(DeploymentFact::degraded).count();
        Double failureRate = finished.isEmpty() ? null : 100.0 * (pipelineFailures + degradations) / finished.size();
        metrics.add(new DoraMetric("change_failure_rate", "Taux d'échec des changements", failureRate == null ? null : round(failureRate, 1), "%",
                failureRate == null ? "NONE" : failureRate <= 15 ? "ELITE" : failureRate <= 30 ? "HIGH" : failureRate <= 45 ? "MEDIUM" : "LOW",
                pipelineFailures + " échec(s) au pipeline et " + degradations + " dégradation(s) sur " + finished.size() + " changement(s)",
                "Part des changements qui ont échoué au déploiement ou dégradé l'application après leur mise en service."));

        // 4. Temps de rétablissement
        List<Double> restoreMinutes = new ArrayList<>();
        for (DeploymentFact incident : deployed.stream().filter(DeploymentFact::degraded).toList()) {
            restoredAt(incident, facts).ifPresent(at -> restoreMinutes.add(minutes(incident.finishedAt(), at)));
        }
        Double restore = median(restoreMinutes);
        metrics.add(new DoraMetric("time_to_restore", "Temps de rétablissement", restore == null ? null : round(restore, 1), "minutes (médiane)",
                restore == null ? "NONE" : restore < 60 ? "ELITE" : restore < 24 * 60 ? "HIGH" : restore < 7 * 24 * 60 ? "MEDIUM" : "LOW",
                restoreMinutes.size() + " incident(s) rétabli(s)",
                "Médiane du temps entre la mise en service d'une version dégradée et la mise en service suivante réussie "
                        + "(souvent un retour arrière) sur le même environnement."));
        return metrics;
    }

    /** Première mise en service réussie après l'incident, sur le même environnement. */
    static Optional<LocalDateTime> restoredAt(DeploymentFact incident, List<DeploymentFact> facts) {
        if (incident.finishedAt() == null) {
            return Optional.empty();
        }
        return facts.stream()
                .filter(f -> f.success() && !f.degraded() && f.finishedAt() != null)
                .filter(f -> f.environmentId().equals(incident.environmentId()))
                .filter(f -> f.createdAt().isAfter(incident.createdAt()))
                .map(DeploymentFact::finishedAt)
                .filter(at -> at.isAfter(incident.finishedAt()))
                .min(Comparator.naturalOrder());
    }

    static Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static double minutes(LocalDateTime from, LocalDateTime to) {
        return Duration.between(from, to).toSeconds() / 60.0;
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
