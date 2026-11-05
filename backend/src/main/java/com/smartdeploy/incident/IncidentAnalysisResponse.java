package com.smartdeploy.incident;

import java.time.LocalDateTime;
import java.util.List;

public record IncidentAnalysisResponse(
        Long id,
        Long deploymentId,
        String trigger,
        String code,
        String title,
        String confidence,
        String explanation,
        String action,
        List<String> evidence,
        List<String> alternatives,
        List<String> notes,
        LocalDateTime createdAt
) {
}
