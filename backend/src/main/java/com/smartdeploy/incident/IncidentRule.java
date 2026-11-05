package com.smartdeploy.incident;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Signature d'une panne connue : des motifs recherchés dans les preuves,
 * l'étape du pipeline où elle survient habituellement, et ce qu'il faut faire.
 */
public record IncidentRule(
        String code,
        String title,
        String explanation,
        String action,
        Pattern stage,
        List<Pattern> patterns,
        List<IncidentEvidence.Source> sources,
        boolean symptom
) {
    /** Signature d'une cause (par défaut). */
    public IncidentRule(String code, String title, String explanation, String action,
                        Pattern stage, List<Pattern> patterns, List<IncidentEvidence.Source> sources) {
        this(code, title, explanation, action, stage, patterns, sources, false);
    }

    /** Un symptôme (plantage en boucle, sondes en échec) s'efface devant une cause identifiée. */
    IncidentRule asSymptom() {
        return new IncidentRule(code, title, explanation, action, stage, patterns, sources, true);
    }

    static Pattern regex(String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
    }
}
