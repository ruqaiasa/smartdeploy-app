package com.smartdeploy.alert;

import java.time.LocalDateTime;

public record AlertResponse(
        Long id,
        Long applicationId,
        String applicationName,
        Long environmentId,
        String environmentName,
        Long deploymentId,
        AlertType type,
        AlertSeverity severity,
        AlertStatus status,
        String title,
        String message,
        String metricName,
        Double currentValue,
        Double thresholdValue,
        LocalDateTime createdAt,
        LocalDateTime lastDetectedAt,
        LocalDateTime resolvedAt
) {
}