package com.smartdeploy.monitoring.health;

import org.springframework.stereotype.Service;

@Service
public class HealthEvaluationService {

    public HealthStatus evaluate(
            double cpuUsagePercent,
            double memoryUsageMb,
            int runningPods,
            int desiredPods,
            int restartCount
    ) {

        if (desiredPods <= 0) {
            return HealthStatus.UNKNOWN;
        }

        if (runningPods == 0) {
            return HealthStatus.CRITICAL;
        }

        if (runningPods < desiredPods) {
            return HealthStatus.CRITICAL;
        }

        if (restartCount >= 5) {
            return HealthStatus.CRITICAL;
        }

        if (restartCount >= 2) {
            return HealthStatus.WARNING;
        }

        return HealthStatus.HEALTHY;
    }
}