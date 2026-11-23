package com.smartdeploy.portal;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.ApplicationAccess;
import com.smartdeploy.application.ApplicationRepository;
import com.smartdeploy.backup.BackupProtection;
import com.smartdeploy.backup.BackupService;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.guard.DeploymentGuardRepository;
import com.smartdeploy.guard.GuardStatus;
import com.smartdeploy.user.User;
import com.smartdeploy.version.Version;
import com.smartdeploy.version.VersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fiche d'excellence d'une application (à la manière des « scorecards » des portails de développeurs) :
 * une note de A à E sur cinq axes, calculée à partir de données que SmartDeploy possède déjà, avec la
 * recommandation de chaque point faible et la progression dans le temps.
 */
@RestController
public class ApplicationScorecardController {

    public record Scorecard(Long applicationId, String applicationName, Integer score, String grade, Integer previousScore,
                            List<ScorecardCalculator.Axis> axes, List<ScorecardCalculator.Criterion> criteria,
                            List<ScorecardCalculator.Criterion> recommendations, List<Integer> history, LocalDateTime computedAt) {
    }

    public record ScorecardSummary(Long applicationId, String applicationName, Integer score, String grade) {
    }

    private record CachedReport(CompatibilityReport report, long at) {
    }

    private final ApplicationRepository applications;
    private final VersionRepository versions;
    private final EnvironmentRepository environments;
    private final DeploymentRepository deployments;
    private final DeploymentGuardRepository guards;
    private final BackupService backups;
    private final RepositoryInspector inspector;
    private final ScorecardSnapshotRepository snapshots;
    private final Map<String, CachedReport> reportCache = new ConcurrentHashMap<>();

    public ApplicationScorecardController(ApplicationRepository applications, VersionRepository versions,
                                          EnvironmentRepository environments, DeploymentRepository deployments,
                                          DeploymentGuardRepository guards, BackupService backups,
                                          RepositoryInspector inspector, ScorecardSnapshotRepository snapshots) {
        this.applications = applications;
        this.versions = versions;
        this.environments = environments;
        this.deployments = deployments;
        this.guards = guards;
        this.backups = backups;
        this.inspector = inspector;
        this.snapshots = snapshots;
    }

    @GetMapping("/api/portal/applications/{id}/scorecard")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    @Transactional
    public Scorecard scorecard(@PathVariable Long id,
                               @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean refresh) {
        Application app = applications.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application introuvable"));
        ApplicationAccess.ensureCurrentUserCanAccess(app);
        if (refresh) {
            reportCache.keySet().removeIf(k -> k.startsWith(app.getRepositoryUrl() + "#"));   // relire le dépôt
        }
        ScorecardCalculator.Result result = ScorecardCalculator.compute(criteria(app));
        List<ScorecardSnapshot> past = snapshots.findTop10ByApplicationIdOrderByCreatedAtDesc(app.getId());
        record(app.getId(), result, past);
        List<ScorecardSnapshot> history = snapshots.findTop10ByApplicationIdOrderByCreatedAtDesc(app.getId());
        Integer previous = history.stream().skip(1).findFirst().map(ScorecardSnapshot::getScore).orElse(null);
        List<Integer> trend = new ArrayList<>(history.stream().map(ScorecardSnapshot::getScore).toList());
        Collections.reverse(trend);
        return new Scorecard(app.getId(), app.getName(), result.score(), result.grade(), previous, result.axes(),
                result.criteria(), result.recommendations(), trend, LocalDateTime.now());
    }

    /** Les notes de mes applications (le propriétaire), ou de toutes pour l'équipe. */
    @GetMapping("/api/portal/scorecards")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
    @Transactional(readOnly = true)
    public List<ScorecardSummary> summaries(@AuthenticationPrincipal User me) {
        List<Application> mine = ApplicationAccess.isRestricted(me)
                ? applications.findAllByOwner_IdAndDeletedFalseOrderByNameAsc(me.getId())
                : applications.findAll().stream().filter(a -> !a.isDeleted()).toList();
        List<ScorecardSummary> list = new ArrayList<>();
        for (Application a : mine) {
            ScorecardSnapshot last = snapshots.findTop10ByApplicationIdOrderByCreatedAtDesc(a.getId()).stream().findFirst().orElse(null);
            list.add(new ScorecardSummary(a.getId(), a.getName(), last == null ? null : last.getScore(), last == null ? "?" : last.getGrade()));
        }
        return list;
    }

    /* ------------------------------------------------------------------------------------- critères */

    List<ScorecardCalculator.Criterion> criteria(Application app) {
        List<ScorecardCalculator.Criterion> c = new ArrayList<>();
        CompatibilityReport report = report(app);
        Map<String, CompatibilityReport.Status> checks = new HashMap<>();
        if (report != null) {
            report.checks().forEach(k -> checks.put(k.id(), k.status()));
        }
        List<Deployment> recent = deployments.findTop20ByVersion_Application_IdAndDeletedFalseOrderByCreatedAtDesc(app.getId());
        List<Deployment> finished = recent.stream().filter(d -> d.getStatus() == DeploymentStatus.SUCCESS
                || d.getStatus() == DeploymentStatus.FAILED || d.getStatus() == DeploymentStatus.ROLLED_BACK).toList();

        // Qualité
        c.add(check(checks, "tests", "QUALITY", "Tests automatisés présents", 40,
                "Ajoutez des tests unitaires dans src/test : ils sont exécutés à chaque déploiement."));
        boolean qualityGateFailed = recent.stream().anyMatch(d -> d.getStatus() == DeploymentStatus.FAILED && contains(d, "Quality Gate"));
        c.add(new ScorecardCalculator.Criterion("QUALITY", "Seuil de qualité SonarQube atteint", recent.isEmpty() ? null : !qualityGateFailed, 30,
                qualityGateFailed ? "Un déploiement récent s'est arrêté au seuil de qualité" : "Aucun arrêt récent au seuil de qualité",
                "Consultez le rapport SonarQube et corrigez les défauts signalés (duplications, couverture, bogues)."));
        c.add(check(checks, "spring-boot", "QUALITY", "Spring Boot maintenu (3 ou plus)", 15,
                "Passez à Spring Boot 3 ou plus récent."));
        c.add(check(checks, "java", "QUALITY", "Java 17 ou plus", 15, "Passez à Java 17 ou plus récent."));

        // Sécurité
        c.add(check(checks, "secrets", "SECURITY", "Aucun mot de passe en clair dans le dépôt", 40,
                "Remplacez les mots de passe écrits dans la configuration par des variables d'environnement."));
        boolean trivyBlocked = recent.stream().anyMatch(d -> d.getStatus() == DeploymentStatus.FAILED && contains(d, "Trivy"));
        c.add(new ScorecardCalculator.Criterion("SECURITY", "Aucune vulnérabilité critique bloquante", recent.isEmpty() ? null : !trivyBlocked, 40,
                trivyBlocked ? "Un déploiement récent a été bloqué par Trivy" : "Aucun blocage récent par Trivy",
                "Mettez à jour la dépendance signalée par Trivy, puis créez une nouvelle version."));
        c.add(check(checks, "spring-boot", "SECURITY", "Dépendances maintenues", 20,
                "Mettez à jour le cadre logiciel : les anciennes versions ne reçoivent plus de correctifs de sécurité."));

        // Fiabilité
        long ok = finished.stream().filter(d -> d.getStatus() != DeploymentStatus.FAILED).count();
        Integer rate = finished.isEmpty() ? null : (int) Math.round(ok * 100.0 / finished.size());
        c.add(new ScorecardCalculator.Criterion("RELIABILITY", "Taux de réussite des déploiements (80 % ou plus)",
                rate == null ? null : rate >= 80, 50, rate == null ? "Aucun déploiement terminé" : rate + " % de réussite sur les " + finished.size() + " derniers",
                "Vérifiez votre version avant de la demander (compilation et tests en local)."));
        List<Long> ids = recent.stream().map(Deployment::getId).toList();
        long degraded = ids.isEmpty() ? 0 : guards.findByDeploymentIdIn(ids).stream().filter(g -> g.getStatus() == GuardStatus.DEGRADED).count();
        c.add(new ScorecardCalculator.Criterion("RELIABILITY", "Aucune version dégradée récemment", recent.isEmpty() ? null : degraded == 0, 50,
                degraded == 0 ? "Le gardien n'a retiré aucune version" : degraded + " version(s) retirée(s) par le gardien",
                "Lisez l'analyse de la dernière dégradation sur la fiche du déploiement, et corrigez sa cause."));

        // Rapidité
        List<Long> durations = finished.stream().filter(d -> d.getStatus() != DeploymentStatus.FAILED && d.getDurationSeconds() != null)
                .map(Deployment::getDurationSeconds).sorted().toList();
        Long median = durations.isEmpty() ? null : durations.get(durations.size() / 2);
        c.add(new ScorecardCalculator.Criterion("SPEED", "Déploiement en moins de 10 minutes", median == null ? null : median <= 600, 50,
                median == null ? "Aucune durée mesurée" : "Durée médiane : " + (median / 60) + " min " + (median % 60) + " s",
                "Réduisez le temps de construction (dépendances en cache, tests plus rapides)."));
        long lastMonth = recent.stream().filter(d -> d.getCreatedAt() != null && d.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30))).count();
        c.add(new ScorecardCalculator.Criterion("SPEED", "Livraisons fréquentes (2 ou plus en 30 jours)", lastMonth >= 2, 50,
                lastMonth + " déploiement(s) en 30 jours", "Livrez par petites versions, plus souvent : le risque de chaque déploiement diminue."));

        // Exploitation
        c.add(check(checks, "metriques", "OPERATIONS", "Métriques applicatives exposées", 40,
                "Ajoutez micrometer-registry-prometheus : la supervision verra le comportement de votre application, pas seulement ses pods."));
        Set<Long> envIds = new HashSet<>();
        for (Environment e : environments.findAllByApplication_IdAndDeletedFalse(app.getId())) {
            envIds.add(e.getId());
        }
        Boolean protectedEnv = null;
        try {
            List<BackupProtection.EnvironmentProtection> protection = backups.protection().stream()
                    .filter(p -> envIds.contains(p.environmentId())).toList();
            protectedEnv = protection.isEmpty() ? null : protection.stream().anyMatch(p -> p.level() == BackupProtection.Level.OK);
        } catch (RuntimeException ignored) {
            // Velero injoignable : critère inconnu
        }
        c.add(new ScorecardCalculator.Criterion("OPERATIONS", "Données sauvegardées récemment", protectedEnv, 40,
                protectedEnv == null ? "Aucun environnement déployé, ou sauvegardes indisponibles" : protectedEnv ? "Sauvegarde récente réussie" : "Aucune sauvegarde récente réussie",
                "Demandez une sauvegarde de votre environnement (page Sauvegardes de l'équipe DevOps)."));
        c.add(new ScorecardCalculator.Criterion("OPERATIONS", "Sondes de santé et ressources définies", recent.isEmpty() ? null : true, 20,
                "Générées automatiquement par SmartDeploy", null));
        return c;
    }

    private static ScorecardCalculator.Criterion check(Map<String, CompatibilityReport.Status> checks, String id, String axis,
                                                       String label, int points, String recommendation) {
        CompatibilityReport.Status s = checks.get(id);
        Boolean passed = s == null ? null : s == CompatibilityReport.Status.OK;
        String detail = s == null ? "Dépôt non analysé" : passed ? "Conforme" : "À améliorer";
        return new ScorecardCalculator.Criterion(axis, label, passed, points, detail, recommendation);
    }

    private static boolean contains(Deployment d, String text) {
        return d.getErrorMessage() != null && d.getErrorMessage().contains(text);
    }

    /** Le contrôle du dépôt est gardé 15 minutes (GitHub limite les requêtes anonymes). */
    private CompatibilityReport report(Application app) {
        String branch = versions.findAllByApplicationAndDeletedFalse(app).stream()
                .max(Comparator.comparing(Version::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(Version::getGitBranch).orElse("main");
        String key = app.getRepositoryUrl() + "#" + branch;
        CachedReport cached = reportCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.at() < 15 * 60_000L) {
            return cached.report();
        }
        try {
            CompatibilityReport report = CompatibilityAnalyzer.analyze(inspector.inspect(app.getRepositoryUrl(), branch));
            reportCache.put(key, new CachedReport(report, System.currentTimeMillis()));
            return report;
        } catch (RuntimeException exception) {
            return cached == null ? null : cached.report();
        }
    }

    /** Une nouvelle mesure de l'historique : si la note change, ou au plus une fois par heure. */
    private void record(Long applicationId, ScorecardCalculator.Result result, List<ScorecardSnapshot> past) {
        if (result.score() == null) {
            return;
        }
        ScorecardSnapshot last = past.stream().findFirst().orElse(null);
        if (last != null && last.getScore() == result.score() && last.getCreatedAt().isAfter(LocalDateTime.now().minusHours(1))) {
            return;
        }
        ScorecardSnapshot s = new ScorecardSnapshot();
        s.setApplicationId(applicationId);
        s.setScore(result.score());
        s.setGrade(result.grade());
        s.setCreatedAt(LocalDateTime.now());
        snapshots.save(s);
    }
}
