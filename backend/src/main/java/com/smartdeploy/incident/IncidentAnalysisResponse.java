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
        InitialAnalysis initial,
        // Mémoire des incidents : les incidents passés les plus proches, et comment ils ont été résolus
        List<SimilarIncident> similar
) {
    /** Compatibilité : sans incidents similaires. */
    public IncidentAnalysisResponse(Long id, Long deploymentId, String trigger, String code, String title, String confidence,
                                    String explanation, String action, List<String> evidence, List<String> alternatives,
                                    List<String> notes, LocalDateTime createdAt, List<IncidentFacts.Fact> facts,
                                    String decisiveLine, InitialAnalysis initial) {
        this(id, deploymentId, trigger, code, title, confidence, explanation, action, evidence, alternatives, notes,
                createdAt, facts, decisiveLine, initial, List.of());
    }

    /** Copie de la réponse, enrichie des incidents similaires. */
    public IncidentAnalysisResponse withSimilar(List<SimilarIncident> found) {
        return new IncidentAnalysisResponse(id, deploymentId, trigger, code, title, confidence, explanation, action,
                evidence, alternatives, notes, createdAt, facts, decisiveLine, initial, found);
    }

    /**
     * Un incident passé proche : sa similarité (0 à 100), s'il a la même cause, les mots qu'il partage,
     * et ce qui l'a résolu dans son environnement (null : durée inconnue).
     */
    public record SimilarIncident(Long deploymentId, String version, String environmentName, LocalDateTime at,
                                  int similarity, String title, boolean sameCause, List<String> sharedTerms,
                                  String resolution, Long resolutionMinutes) {
    }

    /**
     * La première analyse, faite au moment de l'incident, quand l'analyse affichée a été
     * relancée plus tard : les pods remplacés emportent leurs logs, la première reste la plus complète.
     */
    public record InitialAnalysis(Long id, String title, String confidence, LocalDateTime createdAt,
                                  List<String> evidence, List<IncidentFacts.Fact> facts, String decisiveLine) {
    }
}
