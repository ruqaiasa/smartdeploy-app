package com.smartdeploy.incident;

import java.util.List;

/**
 * Diagnostic d'un incident : la cause retenue, son niveau de confiance,
 * l'action proposée et les lignes de preuve qui la justifient.
 */
public record IncidentDiagnosis(
        String code,
        String title,
        Confidence confidence,
        String explanation,
        String action,
        List<String> evidence,
        List<String> alternatives
) {
    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    public static final String UNKNOWN = "UNKNOWN";
}
