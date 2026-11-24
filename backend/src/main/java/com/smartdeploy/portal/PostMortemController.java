package com.smartdeploy.portal;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentReadAccess;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.EnvironmentType;
import com.smartdeploy.guard.DeploymentGuard;
import com.smartdeploy.guard.DeploymentGuardRepository;
import com.smartdeploy.guard.GuardStatus;
import com.smartdeploy.incident.IncidentAnalysis;
import com.smartdeploy.incident.IncidentAnalysisRepository;
import com.smartdeploy.incident.IncidentMemory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Post-mortem automatique : le rapport d'incident d'un déploiement en échec ou dégradé, reconstitué à partir
 * de ce que SmartDeploy sait déjà (chronologie, verdict du gardien, analyse, retour arrière, incidents
 * similaires) : impact, détection, cause, rétablissement, actions et leçons.
 */
@RestController
public class PostMortemController {

    public record Event(LocalDateTime at, String text, String tone) {
    }

    public record PostMortem(Long deploymentId, String title, String severity, String kind, String summary,
                             String applicationName, String environmentName, String environmentType, String version,
                             String requestedBy, LocalDateTime impactStart, LocalDateTime detectedAt, LocalDateTime recoveredAt,
                             Long minutesToDetect, Long minutesToRecover, String impact, String detection,
                             String cause, String causeDetail, String decisiveLine, String confidence,
                             String recovery, List<String> actions, List<String> lessons, List<Event> timeline,
                             LocalDateTime generatedAt) {
    }

    private final DeploymentRepository deployments;
    private final DeploymentGuardRepository guards;
    private final IncidentAnalysisRepository analyses;
    private final IncidentMemory memory;
    private final AnalysisFeedbackRepository feedback;
    private final DeploymentReadAccess readAccess;

    public PostMortemController(DeploymentRepository deployments, DeploymentGuardRepository guards,
                                IncidentAnalysisRepository analyses, IncidentMemory memory,
                                AnalysisFeedbackRepository feedback, DeploymentReadAccess readAccess) {
        this.deployments = deployments;
        this.guards = guards;
        this.analyses = analyses;
        this.memory = memory;
        this.feedback = feedback;
        this.readAccess = readAccess;
    }

    @GetMapping("/api/deployments/{id}/postmortem")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    @Transactional(readOnly = true)
    public PostMortem postMortem(@PathVariable Long id) {
        readAccess.ensureCanRead(id);
        Deployment d = deployments.findById(id).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Déploiement introuvable"));
        Optional<DeploymentGuard> guard = guards.findFirstByDeploymentIdOrderByIdDesc(id);
        boolean degraded = guard.map(g -> g.getStatus() == GuardStatus.DEGRADED).orElse(false);
        boolean failed = d.getStatus() == DeploymentStatus.FAILED;
        if (!degraded && !failed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ce déploiement n'a pas connu d'incident");
        }
        Optional<IncidentAnalysis> analysis = analyses.findFirstByDeploymentIdOrderByIdDesc(id);
        boolean production = d.getEnvironment().getType() == EnvironmentType.PRODUCTION;
        String app = d.getVersion().getApplication().getName();
        String version = d.getVersion().getVersion();
        String env = d.getEnvironment().getName();

        // Rétablissement : le retour arrière automatique du gardien, sinon le déploiement réussi suivant
        Deployment recoveryDeployment = guard.map(DeploymentGuard::getAutoRollbackDeploymentId)
                .flatMap(deployments::findById).orElse(null);
        boolean automatic = recoveryDeployment != null;
        if (recoveryDeployment == null) {
            recoveryDeployment = deployments.findTop10ByEnvironment_IdAndDeletedFalseOrderByCreatedAtDesc(d.getEnvironment().getId()).stream()
                    .filter(x -> x.getStatus() == DeploymentStatus.SUCCESS && x.getCreatedAt() != null && d.getCreatedAt() != null
                            && x.getCreatedAt().isAfter(d.getCreatedAt()))
                    .min(Comparator.comparing(Deployment::getCreatedAt)).orElse(null);
        }

        LocalDateTime impactStart = degraded ? d.getFinishedAt() : null;
        LocalDateTime detectedAt = degraded ? guard.get().getDecidedAt() : d.getFinishedAt();
        LocalDateTime recoveredAt = recoveryDeployment == null ? null
                : recoveryDeployment.getFinishedAt() != null ? recoveryDeployment.getFinishedAt() : recoveryDeployment.getCreatedAt();
        LocalDateTime origin = impactStart != null ? impactStart : d.getStartedAt() != null ? d.getStartedAt() : d.getCreatedAt();
        Long toDetect = minutes(origin, detectedAt);
        Long toRecover = minutes(origin, recoveredAt);

        String severity = degraded ? (production ? "Majeure" : "Mineure") : "Faible";
        String kind = degraded ? "Version dégradée après sa mise en service" : "Échec du pipeline de déploiement";
        String impact = degraded
                ? "La version " + version + " a été mise en service dans " + env + (production ? " (production : des utilisateurs ont pu être touchés)" : "")
                + ", puis s'est dégradée" + (toRecover == null ? "." : " ; durée de l'incident : " + toRecover + " min.")
                : "Aucun impact pour les utilisateurs : le déploiement s'est arrêté avant la mise en service, la version précédente est restée en ligne.";
        String detection = degraded
                ? "Détecté automatiquement par le gardien" + (toDetect == null ? "" : ", " + toDetect + " min après la mise en service") + " : "
                + guard.get().getVerdict()
                : "Détecté par le pipeline" + (d.getErrorMessage() == null ? "." : " : " + d.getErrorMessage());
        String recovery = recoveryDeployment == null
                ? "Pas encore rétabli : aucune version saine n'a été remise en service depuis."
                : (automatic ? "Retour arrière automatique du gardien vers la version " : "Rétabli par le déploiement de la version ")
                + recoveryDeployment.getVersion().getVersion() + " (déploiement #" + recoveryDeployment.getId() + ")"
                + (toRecover == null ? "." : ", " + toRecover + " min après le début de l'incident.");

        List<String> actions = new ArrayList<>();
        analysis.map(IncidentAnalysis::getAction).filter(a -> a != null && !a.isBlank()).ifPresent(actions::add);
        actions.add("Vérifier la correction en local (compilation, tests, démarrage) avant de créer une nouvelle version.");
        if (degraded) {
            actions.add("Rejouer la version corrigée en développement et attendre le verdict du gardien avant toute promotion.");
        }

        List<String> lessons = new ArrayList<>();
        if (degraded) {
            lessons.add("Le gardien a protégé l'environnement : la version a été retirée sans intervention humaine.");
            lessons.add("Le score de confiance de la version " + version + " est désormais abaissé : la prochaine demande le signalera.");
        } else {
            lessons.add("Le pipeline a joué son rôle de barrière : la version défectueuse n'a jamais été mise en service.");
        }
        analysis.ifPresent(a -> {
            try {
                int seen = memory.similar(a).size();
                if (seen > 0) {
                    lessons.add("Incident déjà rencontré : " + seen + " cas similaire(s) dans la mémoire des incidents.");
                }
            } catch (RuntimeException ignored) {
                // la mémoire est un complément
            }
        });
        feedback.findFirstByDeploymentIdOrderByIdDesc(id).ifPresent(f -> lessons.add("Analyse jugée « "
                + (f.getVerdict() == AnalysisFeedback.Verdict.CORRECT ? "juste" : f.getVerdict() == AnalysisFeedback.Verdict.PARTIAL ? "partielle" : "fausse")
                + " » par l'équipe" + (f.getActualCause() == null || f.getActualCause().isBlank() ? "." : " ; cause réelle : " + f.getActualCause())));

        List<Event> timeline = new ArrayList<>();
        DeploymentTimelineController.events(d).forEach(e -> timeline.add(new Event(e.at(), e.text(), e.tone())));
        if (degraded) {
            guard.get();
            if (guard.get().getStartedAt() != null) {
                timeline.add(new Event(guard.get().getStartedAt(), "Le gardien commence la surveillance de la version " + version, "neutral"));
            }
            if (guard.get().getDecidedAt() != null) {
                timeline.add(new Event(guard.get().getDecidedAt(), "Verdict du gardien : Dégradé", "critical"));
            }
        }
        analysis.ifPresent(a -> timeline.add(new Event(a.getCreatedAt(), "Analyse : " + a.getTitle(), "analysis")));
        if (recoveryDeployment != null) {
            if (recoveryDeployment.getCreatedAt() != null) {
                timeline.add(new Event(recoveryDeployment.getCreatedAt(), (automatic ? "Retour arrière automatique lancé" : "Nouveau déploiement lancé")
                        + " : version " + recoveryDeployment.getVersion().getVersion(), "neutral"));
            }
            if (recoveryDeployment.getFinishedAt() != null && recoveryDeployment.getStatus() == DeploymentStatus.SUCCESS) {
                timeline.add(new Event(recoveryDeployment.getFinishedAt(), "Service rétabli : version " + recoveryDeployment.getVersion().getVersion() + " en service", "ok"));
            }
        }
        timeline.removeIf(e -> e.at() == null);
        timeline.sort(Comparator.comparing(Event::at));

        String cause = analysis.map(IncidentAnalysis::getTitle).orElse("Cause non identifiée");
        String summary = (degraded ? "La version " + version + " de " + app + " s'est dégradée après sa mise en service dans " + env
                : "Le déploiement de la version " + version + " de " + app + " vers " + env + " a échoué")
                + ". Cause : " + cause.toLowerCase() + ". " + recovery;
        return new PostMortem(d.getId(), "Rapport d'incident : " + app + " " + version, severity, kind, summary, app, env,
                d.getEnvironment().getType() == null ? null : d.getEnvironment().getType().name(), version,
                d.getRequestedBy() == null ? null : d.getRequestedBy().getEmail(), impactStart, detectedAt, recoveredAt,
                toDetect, toRecover, impact, detection, cause, analysis.map(IncidentAnalysis::getExplanation).orElse(null),
                analysis.map(IncidentAnalysis::getDecisiveLine).orElse(null), analysis.map(IncidentAnalysis::getConfidence).orElse(null),
                recovery, actions, lessons, timeline, LocalDateTime.now());
    }

    static Long minutes(LocalDateTime from, LocalDateTime to) {
        return from == null || to == null || to.isBefore(from) ? null : Math.max(0, Duration.between(from, to).toMinutes());
    }
}
