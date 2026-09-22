package com.smartdeploy.monitoring;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartdeploy.monitoring.dto.ApplicationMetricsResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import com.smartdeploy.monitoring.dto.ApplicationMetricsHistoryResponse;
import com.smartdeploy.monitoring.dto.DeploymentImpactResponse;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final MonitoringService monitoringService;

    public MonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/targets")
    public ResponseEntity<String> getTargetsStatus() {
        return ResponseEntity.ok(monitoringService.getTargetsStatus());
    }

    @GetMapping("/applications/{applicationName}/metrics")
    public ResponseEntity<ApplicationMetricsResponse> getApplicationMetrics(
            @PathVariable String applicationName,
            @RequestParam String namespace
    ) {
        return ResponseEntity.ok(
                monitoringService.getApplicationMetrics(
                        namespace,
                        applicationName
                )
        );
    }

    @GetMapping("/applications/{applicationName}/metrics/history")
    public ResponseEntity<ApplicationMetricsHistoryResponse>
    getApplicationMetricsHistory(
            @PathVariable String applicationName,
            @RequestParam String namespace,
            @RequestParam long start,
            @RequestParam long end,
            @RequestParam(defaultValue = "60") int step
    ) {

        return ResponseEntity.ok(
                monitoringService.getApplicationMetricsHistory(
                        namespace,
                        applicationName,
                        start,
                        end,
                        step
                )
        );
    }
    @GetMapping("/deployments/{deploymentId}/impact")
    public ResponseEntity<DeploymentImpactResponse> getDeploymentImpact(
            @PathVariable Long deploymentId
    ) {
        return ResponseEntity.ok(
                monitoringService.getDeploymentImpact(deploymentId)
        );
    }

    @GetMapping("/environments/{environmentId}/applications/{applicationName}/metrics")
    public ResponseEntity<ApplicationMetricsResponse>
    getApplicationMetricsAndEvaluateAlerts(
            @PathVariable Long environmentId,
            @PathVariable String applicationName
    ) {
        return ResponseEntity.ok(
                monitoringService.getApplicationMetricsAndEvaluateAlerts(
                        environmentId,
                        applicationName
                )
        );
    }
}