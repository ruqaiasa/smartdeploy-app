package com.smartdeploy.monitoring;

import com.smartdeploy.alert.AlertRepository;
import com.smartdeploy.application.ApplicationAccess;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.monitoring.dto.MetricPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Supervision dans le temps : les courbes d'un environnement (processeur et mémoire comparés à leurs limites,
 * pods prêts, redémarrages), avec les déploiements et les alertes de la période, pour voir l'effet d'une version.
 */
@RestController
public class EnvironmentSeriesController {

    public record Marker(long at, String kind, String label, String tone, Long deploymentId) {
    }

    public record Series(Long environmentId, String applicationName, String environmentName, String namespace,
                         long from, long to, int step,
                         List<MetricPoint> cpu, List<MetricPoint> memory, List<MetricPoint> readyPods,
                         List<MetricPoint> desiredPods, List<MetricPoint> restarts,
                         Double cpuLimit, Double memoryLimit, List<Marker> markers, String error) {
    }

    private final EnvironmentRepository environments;
    private final DeploymentRepository deployments;
    private final AlertRepository alerts;
    private final PrometheusClient prometheus;

    public EnvironmentSeriesController(EnvironmentRepository environments, DeploymentRepository deployments,
                                       AlertRepository alerts, PrometheusClient prometheus) {
        this.environments = environments;
        this.deployments = deployments;
        this.alerts = alerts;
        this.prometheus = prometheus;
    }

    @GetMapping("/api/monitoring/environments/{id}/series")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    @Transactional(readOnly = true)
    public Series series(@PathVariable Long id, @RequestParam(defaultValue = "1") int hours) {
        Environment env = environments.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Environnement introuvable"));
        ApplicationAccess.ensureCurrentUserCanAccess(env.getApplication());
        int h = Math.max(1, Math.min(hours, 168));
        long to = System.currentTimeMillis() / 1000;
        long from = to - h * 3600L;
        int step = Math.max(15, h * 3600 / 120);
        String ns = env.getNamespace();
        String c = "namespace=\"" + ns + "\",container!=\"\",container!=\"POD\"";

        List<Marker> markers = new java.util.ArrayList<>();
        LocalDateTime since = LocalDateTime.ofEpochSecond(from, 0, java.time.ZoneOffset.UTC).atZone(java.time.ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        deployments.findByEnvironment_IdAndCreatedAtAfterAndDeletedFalseOrderByCreatedAtAsc(id, since).forEach(d -> {
            LocalDateTime at = d.getFinishedAt() != null ? d.getFinishedAt() : d.getStartedAt() != null ? d.getStartedAt() : d.getCreatedAt();
            String tone = switch (d.getStatus()) {
                case SUCCESS -> "ok";
                case FAILED -> "critical";
                case ROLLED_BACK -> "muted";
                default -> "running";
            };
            markers.add(new Marker(epoch(at), "DEPLOYMENT", d.getVersion().getVersion(), tone, d.getId()));
        });
        alerts.findByEnvironment_IdAndCreatedAtAfterOrderByCreatedAtAsc(id, since).forEach(a ->
                markers.add(new Marker(epoch(a.getCreatedAt()), "ALERT", a.getTitle(),
                        a.getSeverity() != null && a.getSeverity().name().equals("CRITICAL") ? "critical" : "warning", null)));

        try {
            return new Series(id, env.getApplication().getName(), env.getName(), ns, from, to, step,
                    range("sum(rate(container_cpu_usage_seconds_total{" + c + "}[2m])) * 1000", from, to, step),
                    range("sum(container_memory_working_set_bytes{" + c + "}) / 1024 / 1024", from, to, step),
                    range("sum(kube_deployment_status_replicas_available{namespace=\"" + ns + "\"}) + sum(kube_statefulset_status_replicas_ready{namespace=\"" + ns + "\"} or vector(0))", from, to, step),
                    range("sum(kube_deployment_spec_replicas{namespace=\"" + ns + "\"}) + sum(kube_statefulset_replicas{namespace=\"" + ns + "\"} or vector(0))", from, to, step),
                    range("sum(increase(kube_pod_container_status_restarts_total{namespace=\"" + ns + "\"}[" + Math.max(2, step / 60 + 1) + "m]))", from, to, step),
                    value("sum(kube_pod_container_resource_limits{namespace=\"" + ns + "\",resource=\"cpu\"}) * 1000"),
                    value("sum(kube_pod_container_resource_limits{namespace=\"" + ns + "\",resource=\"memory\"}) / 1024 / 1024"),
                    markers, null);
        } catch (RuntimeException exception) {
            return new Series(id, env.getApplication().getName(), env.getName(), ns, from, to, step,
                    List.of(), List.of(), List.of(), List.of(), List.of(), null, null, markers,
                    "Prometheus est injoignable : " + exception.getMessage());
        }
    }

    private List<MetricPoint> range(String query, long from, long to, int step) {
        return prometheus.queryRangePoints(query, from, to, step);
    }

    private Double value(String query) {
        try {
            OptionalDouble v = prometheus.queryOptionalValue(query);
            return v.isPresent() ? Math.round(v.getAsDouble() * 10) / 10.0 : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static long epoch(LocalDateTime at) {
        return at == null ? 0 : at.atZone(ZoneId.systemDefault()).toEpochSecond();
    }
}
