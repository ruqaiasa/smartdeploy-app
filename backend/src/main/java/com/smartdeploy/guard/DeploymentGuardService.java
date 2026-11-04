package com.smartdeploy.guard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Gardien de déploiement : surveille une application pendant la fenêtre qui
 * suit sa mise en service, compare ses métriques à celles d'avant, et décide.
 *
 * - « Dégradé » n'est déclaré qu'après plusieurs évaluations critiques
 *   consécutives, pour éviter les faux positifs d'un instant.
 * - En cas de dégradation, un retour arrière vers le déploiement précédent
 *   est proposé ; la décision reste humaine dans cette version.
 * - Chaque verdict est inscrit au journal d'activité, avec ses preuves.
 * - Prometheus est interrogé hors transaction.
 */
@Service
public class DeploymentGuardService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentGuardService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    static final String GUARD_ACTOR = "Gardien de déploiement";
    /** Nombre d'évaluations consécutives pour qu'un avertissement soit retenu. */
    static final int PERSISTENT_WARNINGS = 3;

    private final DeploymentGuardRepository guardRepository;
    private final DeploymentRepository deploymentRepository;
    private final GuardMetricsCollector collector;
    private final GuardProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Autowired
    public DeploymentGuardService(
            DeploymentGuardRepository guardRepository,
            DeploymentRepository deploymentRepository,
            GuardMetricsCollector collector,
            GuardProperties properties,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager
    ) {
        this(guardRepository, deploymentRepository, collector, properties, eventPublisher,
                requiresNew(transactionManager), Clock.systemDefaultZone());
    }

    /**
     * Transaction propre : le démarrage est appelé après la validation de la
     * transaction du webhook, qu'il ne faut pas rejoindre.
     */
    private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    DeploymentGuardService(
            DeploymentGuardRepository guardRepository,
            DeploymentRepository deploymentRepository,
            GuardMetricsCollector collector,
            GuardProperties properties,
            ApplicationEventPublisher eventPublisher,
            TransactionOperations transactions,
            Clock clock
    ) {
        this.guardRepository = guardRepository;
        this.deploymentRepository = deploymentRepository;
        this.collector = collector;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ démarrage

    /** Démarre la surveillance d'un déploiement qui vient de réussir. */
    public void start(Long deploymentId) {
        if (!properties.enabled()) {
            return;
        }
        transactions.executeWithoutResult(status -> {
            Deployment deployment = deploymentRepository.findByIdAndDeletedFalse(deploymentId).orElse(null);
            if (deployment == null || deployment.getStatus() != DeploymentStatus.SUCCESS
                    || deployment.getKubernetesDeploymentName() == null
                    || deployment.getKubernetesDeploymentName().isBlank()) {
                return;
            }
            Long environmentId = deployment.getEnvironment().getId();
            LocalDateTime now = LocalDateTime.now(clock);

            // Un seul gardien actif par environnement : le précédent est remplacé.
            for (DeploymentGuard previous : guardRepository.findByEnvironmentIdAndStatus(environmentId, GuardStatus.WATCHING)) {
                previous.setStatus(GuardStatus.SUPERSEDED);
                previous.setVerdict("Surveillance interrompue : le déploiement #" + deploymentId + " a pris le relais.");
                previous.setDecidedAt(now);
            }

            guardRepository.save(DeploymentGuard.builder()
                    .deploymentId(deploymentId)
                    .environmentId(environmentId)
                    .namespace(deployment.getEnvironment().getNamespace())
                    .applicationPrefix(GuardQueries.applicationPrefix(deployment.getKubernetesDeploymentName()))
                    .status(GuardStatus.WATCHING)
                    .verdict("Surveillance en cours pendant " + properties.windowMinutes() + " minutes.")
                    .startedAt(now)
                    .endsAt(now.plusMinutes(properties.windowMinutes()))
                    .build());
        });
        log.info("Gardien : surveillance du déploiement #{} démarrée", deploymentId);
    }

    // ------------------------------------------------------------------ évaluation

    /** Évalue tous les déploiements surveillés (appelé périodiquement). */
    public void evaluateAll() {
        if (!properties.enabled()) {
            return;
        }
        for (DeploymentGuard guard : guardRepository.findByStatus(GuardStatus.WATCHING)) {
            try {
                evaluate(guard.getId());
            } catch (RuntimeException exception) {
                log.error("Gardien : évaluation impossible pour le gardien #{}", guard.getId(), exception);
            }
        }
    }

    void evaluate(Long guardId) {
        DeploymentGuard current = guardRepository.findById(guardId).orElse(null);
        if (current == null || current.getStatus() != GuardStatus.WATCHING) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        long secondsSinceStart = Math.max(0, Duration.between(current.getStartedAt(), now).toSeconds());

        // Hors transaction : Prometheus peut être lent.
        GuardSnapshot snapshot = collector.collect(current.getNamespace(), current.getApplicationPrefix(),
                secondsSinceStart, properties.baselineMinutes());
        GuardEvaluation evaluation = GuardEvaluator.evaluate(snapshot, secondsSinceStart);

        transactions.executeWithoutResult(status -> {
            DeploymentGuard guard = guardRepository.findById(guardId).orElse(null);
            if (guard == null || guard.getStatus() != GuardStatus.WATCHING) {
                return;
            }
            Deployment deployment = deploymentRepository.findByIdAndDeletedFalse(guard.getDeploymentId()).orElse(null);
            if (deployment == null || deployment.getStatus() != DeploymentStatus.SUCCESS) {
                guard.setStatus(GuardStatus.SUPERSEDED);
                guard.setVerdict("Surveillance interrompue : le déploiement n'est plus en service.");
                guard.setDecidedAt(now);
                return;
            }
            apply(guard, deployment, evaluation, snapshot != null, now);
        });
    }

    /** Met à jour le gardien avec une évaluation et décide si la fenêtre est close. */
    void apply(DeploymentGuard guard, Deployment deployment, GuardEvaluation evaluation, boolean hasData, LocalDateTime now) {
        guard.setEvaluations(guard.getEvaluations() + 1);
        if (hasData) {
            guard.setEvaluationsWithData(guard.getEvaluationsWithData() + 1);
        }
        guard.setLastEvaluatedAt(now);
        guard.setChecksJson(toJson(evaluation.checks()));
        guard.setConsecutiveCritical(evaluation.level() == GuardLevel.CRITICAL ? guard.getConsecutiveCritical() + 1 : 0);
        // Un avertissement ne compte que s'il persiste (environ 1 min 30).
        guard.setConsecutiveWarnings(evaluation.level() == GuardLevel.WARNING ? guard.getConsecutiveWarnings() + 1 : 0);
        if (guard.getConsecutiveWarnings() >= PERSISTENT_WARNINGS) {
            guard.setWarningsSeen(true);
        }

        if (guard.getConsecutiveCritical() >= properties.criticalConfirmations()) {
            degrade(guard, deployment, evaluation, now);
            return;
        }
        if (now.isBefore(guard.getEndsAt())) {
            return;
        }
        if (guard.getEvaluationsWithData() == 0) {
            close(guard, deployment, GuardStatus.INCONCLUSIVE, AuditAction.GUARD_INCONCLUSIVE,
                    "Aucune métrique exploitable pendant la surveillance : verdict impossible.", null, now);
        } else if (guard.isWarningsSeen()) {
            close(guard, deployment, GuardStatus.HEALTHY_WITH_WARNINGS, AuditAction.GUARD_PASSED,
                    "Aucune anomalie critique en " + properties.windowMinutes() + " minutes, avec des avertissements.",
                    describe(evaluation.warnings()), now);
        } else {
            close(guard, deployment, GuardStatus.HEALTHY, AuditAction.GUARD_PASSED,
                    "Déploiement sain : aucune anomalie en " + properties.windowMinutes() + " minutes.", null, now);
        }
    }

    private void degrade(DeploymentGuard guard, Deployment deployment, GuardEvaluation evaluation, LocalDateTime now) {
        Optional<Deployment> target = rollbackTarget(deployment);
        guard.setRollbackTargetId(target.map(Deployment::getId).orElse(null));
        String causes = evaluation.critical().stream().map(GuardCheck::label).collect(Collectors.joining(", "));
        String proposal = target
                .map(t -> " Retour proposé au déploiement #" + t.getId() + " (version " + t.getVersion().getVersion() + ").")
                .orElse(deployment.getRollbackOf() != null
                        ? " Ce déploiement est lui-même un retour arrière : aucune cible automatique."
                        : " Aucun déploiement antérieur réutilisable.");
        close(guard, deployment, GuardStatus.DEGRADED, AuditAction.GUARD_DEGRADED,
                "Dégradation confirmée : " + causes + "." + proposal, describe(evaluation.critical()), now);
    }

    /**
     * Cible du retour arrière : le dernier déploiement sain de l'environnement.
     * Après un déploiement normal, le précédent reste « Réussi » (seul un
     * retour arrière marque explicitement le déploiement remplacé) : les
     * candidats sont donc les déploiements réussis ou remplacés, antérieurs,
     * tracés par empreinte, et que le gardien n'a pas lui-même jugés dégradés.
     * Aucune cible si le déploiement dégradé est lui-même un retour arrière.
     */
    Optional<Deployment> rollbackTarget(Deployment deployment) {
        if (deployment.getRollbackOf() != null) {
            return Optional.empty();
        }
        List<Deployment> candidates = new ArrayList<>();
        for (DeploymentStatus status : List.of(DeploymentStatus.SUCCESS, DeploymentStatus.ROLLED_BACK)) {
            candidates.addAll(deploymentRepository
                    .findByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(deployment.getEnvironment(), status));
        }
        return candidates.stream()
                .filter(candidate -> !candidate.getId().equals(deployment.getId()))
                .filter(candidate -> candidate.getCreatedAt() != null && deployment.getCreatedAt() != null
                        && candidate.getCreatedAt().isBefore(deployment.getCreatedAt()))
                .filter(candidate -> candidate.getDeployedBackendImage() != null && !candidate.getDeployedBackendImage().isBlank())
                .filter(candidate -> !judgedDegraded(candidate.getId()))
                .max(Comparator.comparing(Deployment::getCreatedAt));
    }

    private boolean judgedDegraded(Long deploymentId) {
        return guardRepository.findFirstByDeploymentIdOrderByIdDesc(deploymentId)
                .map(guard -> guard.getStatus() == GuardStatus.DEGRADED)
                .orElse(false);
    }

    private void close(DeploymentGuard guard, Deployment deployment, GuardStatus status, AuditAction action,
                       String verdict, String details, LocalDateTime now) {
        guard.setStatus(status);
        guard.setVerdict(verdict);
        guard.setDecidedAt(now);
        eventPublisher.publishEvent(new AuditEvent(
                action, GUARD_ACTOR, "DEPLOYMENT", deployment.getId(),
                deployment.getVersion().getApplication().getName(), deployment.getEnvironment().getName(),
                "Déploiement #" + deployment.getId() + " : " + verdict, details, false));
        log.info("Gardien : déploiement #{} → {}", deployment.getId(), status);
    }

    private static String describe(List<GuardCheck> checks) {
        return checks.stream()
                .map(check -> check.label() + " : " + check.observed()
                        + (check.reference() == null ? "" : " (référence : " + check.reference() + ")"))
                .collect(Collectors.joining(" ; "));
    }

    // ------------------------------------------------------------------ lecture

    public Optional<DeploymentGuardResponse> findForDeployment(Long deploymentId) {
        return guardRepository.findFirstByDeploymentIdOrderByIdDesc(deploymentId).map(this::toResponse);
    }

    /** Dernier gardien de chaque déploiement demandé. */
    public List<DeploymentGuardResponse> findForDeployments(Collection<Long> deploymentIds) {
        return guardRepository.findByDeploymentIdIn(deploymentIds).stream()
                .collect(Collectors.toMap(DeploymentGuard::getDeploymentId, g -> g,
                        (a, b) -> a.getId() > b.getId() ? a : b))
                .values().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<DeploymentGuardResponse> recent(int limit) {
        return guardRepository.findAllByOrderByIdDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 200)))
                .stream().map(this::toResponse).toList();
    }

    DeploymentGuardResponse toResponse(DeploymentGuard guard) {
        return new DeploymentGuardResponse(guard.getId(), guard.getDeploymentId(), guard.getStatus(), guard.getVerdict(),
                fromJson(guard.getChecksJson()), guard.getEvaluations(), guard.getRollbackTargetId(),
                guard.getStartedAt(), guard.getEndsAt(), guard.getLastEvaluatedAt(), guard.getDecidedAt());
    }

    private static String toJson(List<GuardCheck> checks) {
        try {
            return MAPPER.writeValueAsString(checks);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private static List<GuardCheck> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<GuardCheck>>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }
}
