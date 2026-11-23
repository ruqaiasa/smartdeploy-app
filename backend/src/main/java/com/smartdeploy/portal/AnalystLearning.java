package com.smartdeploy.portal;

import com.smartdeploy.incident.IncidentAnalysis;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Analyste apprenant : la confiance d'une analyse est recalibrée par l'expérience. La confiance initiale de
 * l'analyste compte comme {@value #PRIOR_WEIGHT} avis ; les avis réels de l'équipe sur la même cause
 * (juste = 1, partielle = 0,5, fausse = 0) la corrigent : une cause souvent confirmée gagne en confiance,
 * une cause souvent démentie en perd. Calcul simple, explicable et testable.
 */
@Component
public class AnalystLearning {

    static final int PRIOR_WEIGHT = 4;

    public record Learning(String code, int reviews, int confirmed, int partial, int refuted, Integer precision,
                           String baseConfidence, String calibratedConfidence, String note) {
    }

    private final AnalysisFeedbackRepository feedback;

    public AnalystLearning(AnalysisFeedbackRepository feedback) {
        this.feedback = feedback;
    }

    @Transactional(readOnly = true)
    public Learning learn(IncidentAnalysis analysis) {
        // Un seul avis par déploiement : le plus récent
        Map<Long, AnalysisFeedback> latest = new HashMap<>();
        for (AnalysisFeedback f : feedback.findAll()) {
            AnalysisFeedback kept = latest.get(f.getDeploymentId());
            if (kept == null || f.getId() > kept.getId()) {
                latest.put(f.getDeploymentId(), f);
            }
        }
        List<AnalysisFeedback> sameCause = latest.values().stream()
                .filter(f -> Objects.equals(f.getCode(), analysis.getCode()))
                .toList();
        int confirmed = (int) sameCause.stream().filter(f -> f.getVerdict() == AnalysisFeedback.Verdict.CORRECT).count();
        int partial = (int) sameCause.stream().filter(f -> f.getVerdict() == AnalysisFeedback.Verdict.PARTIAL).count();
        int refuted = sameCause.size() - confirmed - partial;
        return of(analysis.getCode(), analysis.getConfidence(), confirmed, partial, refuted);
    }

    static Learning of(String code, String baseConfidence, int confirmed, int partial, int refuted) {
        int reviews = confirmed + partial + refuted;
        double base = switch (baseConfidence == null ? "" : baseConfidence) {
            case "HIGH" -> 0.85;
            case "MEDIUM" -> 0.6;
            default -> 0.35;
        };
        Integer precision = reviews == 0 ? null : (int) Math.round((confirmed + 0.5 * partial) * 100.0 / reviews);
        double posterior = (base * PRIOR_WEIGHT + (confirmed + 0.5 * partial)) / (PRIOR_WEIGHT + reviews);
        String calibrated = posterior >= 0.75 ? "HIGH" : posterior >= 0.5 ? "MEDIUM" : "LOW";
        String note;
        if (reviews == 0) {
            note = "Aucun avis de l'équipe sur cette cause pour l'instant : confiance initiale de l'analyste.";
        } else {
            note = "Cette cause a été confirmée " + confirmed + " fois sur " + reviews
                    + (partial > 0 ? " (" + partial + " partiellement)" : "")
                    + " : précision mesurée de " + precision + " %.";
            if (!calibrated.equals(baseConfidence)) {
                note += calibrated.compareTo(baseConfidence == null ? "" : baseConfidence) != 0 && rank(calibrated) > rank(baseConfidence)
                        ? " La confiance est relevée par l'expérience."
                        : " La confiance est abaissée par l'expérience.";
            }
        }
        return new Learning(code, reviews, confirmed, partial, refuted, precision, baseConfidence, calibrated, note);
    }

    private static int rank(String level) {
        return "HIGH".equals(level) ? 3 : "MEDIUM".equals(level) ? 2 : 1;
    }
}
