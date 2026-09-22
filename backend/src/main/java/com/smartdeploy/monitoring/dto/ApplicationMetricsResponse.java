package com.smartdeploy.monitoring.dto;

import com.smartdeploy.monitoring.health.HealthStatus;

public record ApplicationMetricsResponse(
        double cpuUsagePercent,
        double memoryUsageMb,
        int runningPods,
        int desiredPods,
        int restartCount,
        HealthStatus healthStatus
) {
}