package com.smartdeploy.monitoring;

import com.smartdeploy.monitoring.dto.MetricPoint;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Capacité du cluster : l'utilisation réelle du processeur et de la mémoire de la machine dans le temps, et la
 * part déjà RÉSERVÉE par les pods (au-delà de 90 %, les nouveaux pods ne peuvent plus démarrer).
 */
@RestController
public class ClusterCapacityController {

    public record Capacity(long from, long to, List<MetricPoint> cpuUsage, List<MetricPoint> memoryUsage,
                           List<MetricPoint> runningPods, Double cpuRequested, Double memoryRequested,
                           Double cpuCores, Double memoryGb, Double runningPodsNow, String error) {
    }

    private final PrometheusClient prometheus;

    public ClusterCapacityController(PrometheusClient prometheus) {
        this.prometheus = prometheus;
    }

    @GetMapping("/api/monitoring/cluster")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVOPS', 'ADMIN')")
    public Capacity capacity(@RequestParam(defaultValue = "24") int hours) {
        int h = Math.max(1, Math.min(hours, 168));
        long to = System.currentTimeMillis() / 1000;
        long from = to - h * 3600L;
        int step = Math.max(60, h * 3600 / 120);
        try {
            return new Capacity(from, to,
                    prometheus.queryRangePoints("100 * (1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[5m])))", from, to, step),
                    prometheus.queryRangePoints("100 * (1 - sum(node_memory_MemAvailable_bytes) / sum(node_memory_MemTotal_bytes))", from, to, step),
                    prometheus.queryRangePoints("sum(kube_pod_status_phase{phase=\"Running\"})", from, to, step),
                    value("100 * sum(kube_pod_container_resource_requests{resource=\"cpu\"} * on(namespace, pod) group_left() (kube_pod_status_phase{phase=~\"Running|Pending\"} == 1)) / sum(kube_node_status_allocatable{resource=\"cpu\"})"),
                    value("100 * sum(kube_pod_container_resource_requests{resource=\"memory\"} * on(namespace, pod) group_left() (kube_pod_status_phase{phase=~\"Running|Pending\"} == 1)) / sum(kube_node_status_allocatable{resource=\"memory\"})"),
                    value("sum(kube_node_status_allocatable{resource=\"cpu\"})"),
                    value("sum(kube_node_status_allocatable{resource=\"memory\"}) / 1024 / 1024 / 1024"),
                    value("sum(kube_pod_status_phase{phase=\"Running\"})"),
                    null);
        } catch (RuntimeException exception) {
            return new Capacity(from, to, List.of(), List.of(), List.of(), null, null, null, null, null,
                    "Prometheus est injoignable : " + exception.getMessage());
        }
    }

    private Double value(String query) {
        try {
            OptionalDouble v = prometheus.queryOptionalValue(query);
            return v.isPresent() ? Math.round(v.getAsDouble() * 10) / 10.0 : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
