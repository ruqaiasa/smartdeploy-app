package com.smartdeploy.dashboard;

import com.smartdeploy.alert.Alert;
import com.smartdeploy.alert.AlertRepository;
import com.smartdeploy.alert.AlertStatus;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.incident.IncidentAnalysis;
import com.smartdeploy.incident.IncidentAnalysisRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Tendances du tableau de bord : l'activité jour par jour, les indicateurs comparés à la période précédente,
 * les causes de panne les plus fréquentes et la santé de chaque environnement.
 */
@RestController
public class DashboardInsightsController {

    public record Day(String date, int success, int failed, int other) {
    }

    /** Un indicateur et sa valeur sur la période précédente, pour montrer la tendance. */
    public record Kpi(String key, String label, Double value, Double previous, String unit, boolean higherIsBetter) {
    }

    public record Cause(String code, String title, int count) {
    }

    public record EnvironmentHealth(Long environmentId, String applicationName, String environmentName, String type,
                                    String version, String state, int openAlerts, String lastDeploymentAt) {
    }

    public record Insights(int days, List<Day> daily, List<Kpi> kpis, List<Cause> causes, List<EnvironmentHealth> environments) {
    }

    private final DeploymentRepository deployments;
    private final IncidentAnalysisRepository analyses;
    private final EnvironmentRepository environments;
    private final AlertRepository alerts;

    public DashboardInsightsController(DeploymentRepository deployments, IncidentAnalysisRepository analyses,
                                       EnvironmentRepository environments, AlertRepository alerts) {
        this.deployments = deployments;
        this.analyses = analyses;
        this.environments = environments;
        this.alerts = alerts;
    }

    @GetMapping("/api/dashboard/insights")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVOPS', 'ADMIN')")
    @Transactional(readOnly = true)
    public Insights insights(@RequestParam(defaultValue = "14") int days) {
        int d = Math.max(7, Math.min(days, 90));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(d);
        LocalDateTime previousStart = start.minusDays(d);
        List<Deployment> all = deployments.findByCreatedAtAfterAndDeletedFalse(previousStart);
        List<Deployment> current = all.stream().filter(x -> x.getCreatedAt().isAfter(start)).toList();
        List<Deployment> previous = all.stream().filter(x -> !x.getCreatedAt().isAfter(start)).toList();

        // Activité jour par jour
        Map<LocalDate, int[]> byDay = new TreeMap<>();
        for (int i = d - 1; i >= 0; i--) {
            byDay.put(now.toLocalDate().minusDays(i), new int[3]);
        }
        for (Deployment x : current) {
            int[] counts = byDay.get(x.getCreatedAt().toLocalDate());
            if (counts == null) {
                continue;
            }
            if (x.getStatus() == DeploymentStatus.SUCCESS || x.getStatus() == DeploymentStatus.ROLLED_BACK) {
                counts[0]++;
            } else if (x.getStatus() == DeploymentStatus.FAILED) {
                counts[1]++;
            } else {
                counts[2]++;
            }
        }
        List<Day> daily = new ArrayList<>();
        byDay.forEach((day, c) -> daily.add(new Day(day.toString(), c[0], c[1], c[2])));

        // Indicateurs, comparés à la période précédente
        List<Kpi> kpis = List.of(
                new Kpi("successRate", "Taux de réussite", successRate(current), successRate(previous), "%", true),
                new Kpi("pipeline", "Durée médiane du pipeline", pipelineMinutes(current), pipelineMinutes(previous), "min", false),
                new Kpi("approval", "Attente médiane d'approbation", approvalMinutes(current), approvalMinutes(previous), "min", false),
                new Kpi("recovery", "Temps médian de rétablissement", recoveryMinutes(current), recoveryMinutes(previous), "min", false),
                new Kpi("volume", "Déploiements", (double) current.size(), (double) previous.size(), "", true));

        // Causes de panne les plus fréquentes
        Map<String, Cause> causes = new LinkedHashMap<>();
        for (IncidentAnalysis a : analyses.findTop500ByOrderByIdDesc()) {
            if (a.getCreatedAt() == null || a.getCreatedAt().isBefore(start) || a.getCode() == null) {
                continue;
            }
            Cause c = causes.get(a.getCode());
            causes.put(a.getCode(), new Cause(a.getCode(), a.getTitle(), c == null ? 1 : c.count() + 1));
        }
        List<Cause> topCauses = causes.values().stream().sorted(Comparator.comparingInt(Cause::count).reversed()).limit(6).toList();

        // Santé de chaque environnement
        Map<Long, Integer> openAlerts = new HashMap<>();
        for (Alert a : alerts.findByStatusOrderByCreatedAtDesc(AlertStatus.OPEN)) {
            if (a.getEnvironment() != null) {
                openAlerts.merge(a.getEnvironment().getId(), 1, Integer::sum);
            }
        }
        List<EnvironmentHealth> envs = new ArrayList<>();
        for (Environment e : environments.findAll()) {
            if (e.isDeleted()) {
                continue;
            }
            List<Deployment> recent = deployments.findTop10ByEnvironment_IdAndDeletedFalseOrderByCreatedAtDesc(e.getId());
            Deployment inService = recent.stream().filter(x -> x.getStatus() == DeploymentStatus.SUCCESS).findFirst().orElse(null);
            Deployment last = recent.isEmpty() ? null : recent.get(0);
            int alertsCount = openAlerts.getOrDefault(e.getId(), 0);
            String state = last == null ? "EMPTY"
                    : last.getStatus() == DeploymentStatus.RUNNING || last.getStatus() == DeploymentStatus.PENDING
                    || last.getStatus() == DeploymentStatus.AWAITING_APPROVAL ? "DEPLOYING"
                    : alertsCount > 0 ? "ALERT"
                    : last.getStatus() == DeploymentStatus.FAILED ? "FAILED"
                    : inService != null ? "HEALTHY" : "IDLE";
            envs.add(new EnvironmentHealth(e.getId(), e.getApplication().getName(), e.getName(),
                    e.getType() == null ? null : e.getType().name(), inService == null ? null : inService.getVersion().getVersion(),
                    state, alertsCount, last == null ? null : last.getCreatedAt().toString()));
        }
        envs.sort(Comparator.comparing(EnvironmentHealth::applicationName).thenComparing(EnvironmentHealth::environmentName));
        return new Insights(d, daily, kpis, topCauses, envs);
    }

    /* ------------------------------------------------------------------------ calculs (testables) */

    static Double successRate(List<Deployment> list) {
        long finished = list.stream().filter(x -> x.getStatus() == DeploymentStatus.SUCCESS
                || x.getStatus() == DeploymentStatus.ROLLED_BACK || x.getStatus() == DeploymentStatus.FAILED).count();
        if (finished == 0) {
            return null;
        }
        long ok = list.stream().filter(x -> x.getStatus() == DeploymentStatus.SUCCESS || x.getStatus() == DeploymentStatus.ROLLED_BACK).count();
        return Math.round(ok * 1000.0 / finished) / 10.0;
    }

    static Double pipelineMinutes(List<Deployment> list) {
        return median(list.stream().filter(x -> x.getStatus() != DeploymentStatus.FAILED && x.getDurationSeconds() != null)
                .map(x -> x.getDurationSeconds() / 60.0).toList());
    }

    static Double approvalMinutes(List<Deployment> list) {
        return median(list.stream().filter(x -> x.getApprovalDecidedAt() != null && x.getCreatedAt() != null)
                .map(x -> Duration.between(x.getCreatedAt(), x.getApprovalDecidedAt()).toSeconds() / 60.0).toList());
    }

    /** Délai entre un échec et le déploiement réussi suivant dans le même environnement. */
    static Double recoveryMinutes(List<Deployment> list) {
        List<Deployment> sorted = list.stream().filter(x -> x.getCreatedAt() != null)
                .sorted(Comparator.comparing(Deployment::getCreatedAt)).toList();
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Deployment failed = sorted.get(i);
            if (failed.getStatus() != DeploymentStatus.FAILED || failed.getFinishedAt() == null) {
                continue;
            }
            for (int j = i + 1; j < sorted.size(); j++) {
                Deployment next = sorted.get(j);
                if (Objects.equals(next.getEnvironment().getId(), failed.getEnvironment().getId())
                        && next.getStatus() == DeploymentStatus.SUCCESS && next.getFinishedAt() != null) {
                    values.add(Duration.between(failed.getFinishedAt(), next.getFinishedAt()).toSeconds() / 60.0);
                    break;
                }
            }
        }
        return median(values);
    }

    static Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> s = values.stream().sorted().toList();
        double m = s.size() % 2 == 1 ? s.get(s.size() / 2) : (s.get(s.size() / 2 - 1) + s.get(s.size() / 2)) / 2;
        return Math.round(m * 10) / 10.0;
    }
}
