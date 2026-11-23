package com.smartdeploy.confidence;

import com.smartdeploy.environment.EnvironmentType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Calcul du score de confiance, sans dépendance : 100 points, moins des
 * pénalités justifiées. Les poids sont volontairement simples et lisibles,
 * pour qu'un utilisateur comprenne chaque point retiré.
 * Règles de justesse : un même échec n'est compté qu'une fois ; seule une cause
 * attribuable au code pénalise la version ; le taux d'échec d'un environnement
 * n'est retenu qu'à partir de trois déploiements terminés.
 */
public final class ConfidenceCalculator {

    /** Une version déjà dégradée est un signal fort : −50, puis −15 par dégradation supplémentaire (au plus −80). */
    static final int VERSION_DEGRADED = 50;
    static final int VERSION_DEGRADED_AGAIN = 15;
    static final int VERSION_DEGRADED_MAX = 80;
    static final int VERSION_FAILED = 25;
    static final int RECENT_FAILURE_MAX = 30;
    static final int RECENT_INCIDENTS = 10;
    static final int NOT_VALIDATED = 25;
    static final int WORK_BRANCH = 5;
    /** En dessous, le taux d'échec d'un environnement n'est pas significatif. */
    static final int MIN_ENVIRONMENT_SAMPLE = 3;
    /**
     * Causes d'échec attribuables au code de la version (diagnostic de l'analyste).
     * Les autres (réseau, registre, délai, base injoignable, cause non identifiée)
     * viennent de l'environnement ou de l'extérieur : elles ne pénalisent pas la version.
     */
    static final Set<String> VERSION_CAUSES = Set.of("COMPILATION_ERROR", "TEST_FAILURE", "QUALITY_GATE",
            "DOCKERFILE_INVALID", "FRONTEND_BUILD", "VULNERABILITY", "CONFIG_MISSING", "CRASH_LOOP", "OUT_OF_MEMORY");

    public static boolean isVersionCause(String incidentCode) {
        return incidentCode != null && VERSION_CAUSES.contains(incidentCode);
    }

    private static final Set<EnvironmentType> EXPOSED = Set.of(EnvironmentType.STAGING, EnvironmentType.PREPRODUCTION, EnvironmentType.PRODUCTION);
    private static final Set<String> MAIN_BRANCHES = Set.of("main", "master");

    private ConfidenceCalculator() {
    }

    public static ConfidenceScore compute(ConfidenceInputs in) {
        List<ConfidenceFactor> factors = new ArrayList<>();

        int degradations = Math.max(in.versionDegradations(), in.versionDegraded() ? 1 : 0);
        if (degradations > 0) {
            int penalty = Math.min(VERSION_DEGRADED_MAX, VERSION_DEGRADED + VERSION_DEGRADED_AGAIN * (degradations - 1));
            factors.add(new ConfidenceFactor("Version déjà jugée dégradée", -penalty,
                    degradations == 1
                            ? "Le gardien a déjà détecté une dégradation après la mise en service de cette version."
                            : "Le gardien a détecté " + degradations + " dégradations après les mises en service de cette version."));
        }
        if (in.versionFailures() > 0) {
            factors.add(new ConfidenceFactor("Version déjà en échec", -VERSION_FAILED,
                    "Cette version a déjà échoué " + in.versionFailures() + " fois à cause de son code (diagnostic de l'analyste)."));
        }
        if (in.recentFinished() >= MIN_ENVIRONMENT_SAMPLE && in.recentFailed() > 0) {
            int penalty = Math.round((float) RECENT_FAILURE_MAX * in.recentFailed() / in.recentFinished());
            if (penalty > 0) {
                factors.add(new ConfidenceFactor("Échecs récents sur l'environnement", -penalty,
                        in.recentFailed() + " échec(s) sur les " + in.recentFinished()
                                + " derniers déploiements de cet environnement, hors échecs déjà comptés pour cette version."));
            }
        }
        if (in.recentIncidents() > 0) {
            factors.add(new ConfidenceFactor("Incidents récents", -RECENT_INCIDENTS,
                    in.recentIncidents() + " mise(s) en service d'autres versions jugée(s) dégradée(s) sur cet environnement depuis 24 heures."));
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
