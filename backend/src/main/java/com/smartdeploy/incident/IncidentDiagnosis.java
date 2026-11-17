package com.smartdeploy.incident;

import java.util.List;

/**
 * Diagnostic d'un incident : la cause retenue, son niveau de confiance,
 * l'action proposée, les lignes de preuve qui la justifient, les faits
 * précis qui en sont extraits et la ligne décisive.
 */
public record IncidentDiagnosis(
        String code,
        String title,
        Confidence confidence,
        String explanation,
        String action,
        List<String> evidence,
        List<String> alternatives,
        List<IncidentFacts.Fact> facts,
        String decisiveLine
) {
    public IncidentDiagnosis {
        facts = facts == null ? List.of() : List.copyOf(facts);
    }

    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    public static final String UNKNOWN = "UNKNOWN";
}
