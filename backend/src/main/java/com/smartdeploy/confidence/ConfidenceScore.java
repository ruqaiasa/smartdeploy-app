package com.smartdeploy.confidence;

import java.util.List;

/**
 * Score de confiance d'un déploiement, de 0 à 100, calculé avant son lancement.
 *
 * @param level HIGH (80 et plus), MEDIUM (50 à 79) ou LOW (moins de 50)
 */
public record ConfidenceScore(int score, String level, String levelLabel, String advice, List<ConfidenceFactor> factors) {
}
