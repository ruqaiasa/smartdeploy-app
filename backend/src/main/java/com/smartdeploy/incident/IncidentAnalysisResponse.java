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
        LocalDateTime createdAt,
        List<IncidentFacts.Fact> facts,
        String decisiveLine,
        InitialAnalysis initial
) {
    /**
     * La première analyse, faite au moment de l'incident, quand l'analyse affichée a été
     * relancée plus tard : les pods remplacés emportent leurs logs, la première reste la plus complète.
     */
    public record InitialAnalysis(Long id, String title, String confidence, LocalDateTime createdAt,
                                  List<String> evidence, List<IncidentFacts.Fact> facts, String decisiveLine) {
    }
}
