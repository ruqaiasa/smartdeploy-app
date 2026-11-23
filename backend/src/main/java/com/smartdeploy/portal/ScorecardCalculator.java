package com.smartdeploy.portal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fiche d'excellence : chaque critère vaut des points dans son axe ; un critère inconnu (donnée absente)
 * n'est pas compté. Note d'un axe = points obtenus / points connus ; note globale = moyenne des axes connus.
 * Calcul pur, testable.
 */
public final class ScorecardCalculator {

    public record Criterion(String axis, String label, Boolean passed, int points, String detail, String recommendation) {
    }

    public record Axis(String key, String label, Integer score) {
    }

    public record Result(Integer score, String grade, List<Axis> axes, List<Criterion> criteria, List<Criterion> recommendations) {
    }

    static final Map<String, String> AXES = new LinkedHashMap<>();

    static {
        AXES.put("QUALITY", "Qualité");
        AXES.put("SECURITY", "Sécurité");
        AXES.put("RELIABILITY", "Fiabilité");
        AXES.put("SPEED", "Rapidité");
        AXES.put("OPERATIONS", "Exploitation");
    }

    private ScorecardCalculator() {
    }

    public static Result compute(List<Criterion> criteria) {
        List<Axis> axes = new ArrayList<>();
        int sum = 0;
        int known = 0;
        for (Map.Entry<String, String> a : AXES.entrySet()) {
            int max = 0;
            int got = 0;
            for (Criterion c : criteria) {
                if (c.axis().equals(a.getKey()) && c.passed() != null) {
                    max += c.points();
                    if (c.passed()) {
                        got += c.points();
                    }
                }
            }
            Integer score = max == 0 ? null : (int) Math.round(got * 100.0 / max);
            axes.add(new Axis(a.getKey(), a.getValue(), score));
            if (score != null) {
                sum += score;
                known++;
            }
        }
        Integer total = known == 0 ? null : (int) Math.round((double) sum / known);
        List<Criterion> recommendations = criteria.stream()
                .filter(c -> Boolean.FALSE.equals(c.passed()) && c.recommendation() != null)
                .sorted((x, y) -> Integer.compare(y.points(), x.points()))
                .toList();
        return new Result(total, grade(total), axes, criteria, recommendations);
    }

    static String grade(Integer score) {
        if (score == null) {
            return "?";
        }
        return score >= 85 ? "A" : score >= 70 ? "B" : score >= 55 ? "C" : score >= 40 ? "D" : "E";
    }
}
