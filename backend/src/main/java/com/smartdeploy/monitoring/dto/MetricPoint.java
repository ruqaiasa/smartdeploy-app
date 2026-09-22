package com.smartdeploy.monitoring.dto;

public record MetricPoint(
        long timestamp,
        double value
) {
}
