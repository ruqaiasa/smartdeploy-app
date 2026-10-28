package com.smartdeploy.monitoring;

import com.smartdeploy.monitoring.dto.ApplicationMetricsHistoryResponse;
import com.smartdeploy.monitoring.dto.ApplicationMetricsResponse;
import com.smartdeploy.monitoring.dto.DeploymentImpactResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monitoring")
@PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
public class MonitoringController {

    private final MonitoringService monitoringService;

    public MonitoringController(
            MonitoringService monitoringService
    ) {
        this.monitoringService = monitoringService;
    }

    /* ==========================================================
     *                  PROMETHEUS TARGETS
     * ==========================================================
     */

    @GetMapping("/targets")
    public ResponseEntity<String> getTargetsStatus() {

        return ResponseEntity.ok(
                monitoringService.getTargetsStatus()
        );
    }

    /* ==========================================================
     *                  APPLICATION METRICS
     * ==========================================================
     */

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

    /* ==========================================================
     *              APPLICATION METRICS HISTORY
     * ==========================================================
     */

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

    /* ==========================================================
     *                  DEPLOYMENT IMPACT
     * ==========================================================
     */

    @GetMapping("/deployments/{deploymentId}/impact")
    public ResponseEntity<DeploymentImpactResponse> getDeploymentImpact(
            @PathVariable Long deploymentId
    ) {

        return ResponseEntity.ok(
                monitoringService.getDeploymentImpact(
                        deploymentId
                )
        );
    }

    /* ==========================================================
     *          ENVIRONMENT APPLICATION METRICS + ALERTS
     * ==========================================================
     */

    @GetMapping(
            "/environments/{environmentId}/applications/{applicationName}/metrics"
    )
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

    /* ==========================================================
     *                  DEPLOYMENT METRICS
     * ==========================================================
     */

    @GetMapping("/deployments/{deploymentId}/metrics")
    public ResponseEntity<ApplicationMetricsResponse> getDeploymentMetrics(
            @PathVariable Long deploymentId
    ) {

        return ResponseEntity.ok(
                monitoringService.getDeploymentMetrics(
                        deploymentId
                )
        );
    }
}