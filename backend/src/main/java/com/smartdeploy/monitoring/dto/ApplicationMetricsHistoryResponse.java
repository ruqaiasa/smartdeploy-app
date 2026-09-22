package com.smartdeploy.monitoring.dto;

import java.util.List;

public record ApplicationMetricsHistoryResponse(
        List<MetricPoint> cpu,
        List<MetricPoint> memory
) {
}