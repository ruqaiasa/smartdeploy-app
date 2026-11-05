package com.smartdeploy.confidence;

import com.smartdeploy.environment.EnvironmentType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Calcul du score de confiance, sans dépendance : 100 points, moins des
 * pénalités justifiées. Les poids sont volontairement simples et lisibles,
 * pour qu'un utilisateur comprenne chaque point retiré.
 */
public final class ConfidenceCalculator {

    static final int VERSION_DEGRADED = 30;
    static final int VERSION_FAILED = 25;
    static final int RECENT_FAILURE_MAX = 30;
    static final int RECENT_INCIDENTS = 10;
    static final int NOT_VALIDATED = 25;
    static final int WORK_BRANCH = 5;

    private static final Set<EnvironmentType> EXPOSED = Set.of(EnvironmentType.STAGING, EnvironmentType.PREPRODUCTION, EnvironmentType.PRODUCTION);
    private static final Set<String> MAIN_BRANCHES = Set.of("main", "master");

    private ConfidenceCalculator() {
    }

    public static ConfidenceScore compute(ConfidenceInputs in) {
        List<ConfidenceFactor> factors = new ArrayList<>();

        if (in.versionDegraded()) {
            factors.add(new ConfidenceFactor("Version déjà jugée dégradée", -VERSION_DEGRADED,
                    "Le gardien a déjà détecté une dégradation après la mise en service de cette version."));
        }
        if (in.versionFailures() > 0) {
            factors.add(new ConfidenceFactor("Version déjà en échec", -VERSION_FAILED,
                    "Cette version a déjà échoué " + in.versionFailures() + " fois au déploiement."));
        }
        if (in.recentFinished() > 0 && in.recentFailed() > 0) {
            int penalty = Math.round((float) RECENT_FAILURE_MAX * in.recentFailed() / in.recentFinished());
            if (penalty > 0) {
                factors.add(new ConfidenceFactor("Échecs récents sur l'environnement", -penalty,
                        in.recentFailed() + " échec(s) sur les " + in.recentFinished() + " derniers déploiements de cet environnement."));
            }
        }
        if (in.recentIncidents() > 0) {
            factors.add(new ConfidenceFactor("Incidents récents", -RECENT_INCIDENTS,
                    in.recentIncidents() + " déploiement(s) touché(s) par un incident sur cet environnement depuis 24 heures."));
        }
        if (EXPOSED.contains(in.environmentType()) && !in.validatedInDevelopment()) {
            factors.add(new ConfidenceFactor("Version non validée en développement", -NOT_VALIDATED,
                    "Cette version n'a encore jamais été déployée avec succès en développement ou en test."));
        }
        if (in.gitBranch() != null && !in.gitBranch().isBlank() && !MAIN_BRANCHES.contains(in.gitBranch().trim())) {
            factors.add(new ConfidenceFactor("Branche de travail", -WORK_BRANCH,
                    "La version provient de la branche « " + in.gitBranch().trim() + " », et non de la branche principale."));
        }

        int score = Math.max(0, 100 + factors.stream().mapToInt(ConfidenceFactor::points).sum());
        if (score >= 80) {
            return new ConfidenceScore(score, "HIGH", "Confiance élevée",
                    "Aucun signal de risque notable : le déploiement peut être lancé.", factors);
        }
        if (score >= 50) {
            return new ConfidenceScore(score, "MEDIUM", "Prudence",
                    "Des signaux de risque existent : surveillez le gardien après la mise en service.", factors);
        }
        return new ConfidenceScore(score, "LOW", "Risque élevé",
                "Plusieurs signaux de risque : corrigez d'abord les causes connues, ou déployez dans un environnement de test.", factors);
    }
}
