package com.smartdeploy.incident;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.guard.GuardQueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Analyste d'incidents : rassemble les preuves d'un échec ou d'une
 * dégradation, les compare au catalogue des pannes connues, enregistre le
 * diagnostic et le trace au journal (donc le notifie).
 * L'analyse est asynchrone : la collecte des logs ne ralentit jamais la
 * réception du statut de Jenkins.
 */
@Service
public class IncidentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(IncidentAnalysisService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    static final String ANALYST_ACTOR = "Analyste d'incidents";
    private static final Map<String, String> CONFIDENCE_LABELS =
            Map.of("HIGH", "confiance élevée", "MEDIUM", "confiance moyenne", "LOW", "confiance faible");

    private final DeploymentRepository deploymentRepository;
    private final IncidentAnalysisRepository analysisRepository;
    private final IncidentEvidenceCollector collector;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate readTransaction;

    public IncidentAnalysisService(DeploymentRepository deploymentRepository,
                                   IncidentAnalysisRepository analysisRepository,
                                   IncidentEvidenceCollector collector,
                                   ApplicationEventPublisher eventPublisher,
                                   PlatformTransactionManager transactionManager) {
        this.deploymentRepository = deploymentRepository;
        this.analysisRepository = analysisRepository;
        this.collector = collector;
        this.eventPublisher = eventPublisher;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Async
    public void analyzeInBackground(Long deploymentId, IncidentTrigger trigger) {
        try {
            analyze(deploymentId, trigger);
        } catch (RuntimeException exception) {
            log.error("Analyse d'incident impossible pour le déploiement #{}", deploymentId, exception);
        }
    }

    public Optional<IncidentAnalysisResponse> analyze(Long deploymentId, IncidentTrigger requested) {
        Context loaded = readTransaction.execute(status -> deploymentRepository.findByIdAndDeletedFalse(deploymentId)
                .map(IncidentAnalysisService::context)
                .orElse(null));
        if (loaded == null) {
            return Optional.empty();
        }
        // Une analyse relancée à la main suit la nature de l'incident.
        IncidentTrigger trigger = requested != IncidentTrigger.MANUAL ? requested
                : loaded.failed() ? IncidentTrigger.PIPELINE : IncidentTrigger.GUARD;

        IncidentEvidence evidence = collector.collect(loaded.context(), trigger);
        IncidentDiagnosis diagnosis = IncidentAnalyzer.analyze(evidence);

        IncidentAnalysis saved = analysisRepository.save(IncidentAnalysis.builder()
                .deploymentId(deploymentId)
                .trigger(requested.name())
                .code(diagnosis.code())
                .title(diagnosis.title())
                .confidence(diagnosis.confidence().name())
                .explanation(diagnosis.explanation())
                .action(diagnosis.action())
                .evidenceJson(json(diagnosis.evidence()))
                .alternativesJson(json(diagnosis.alternatives()))
                .notesJson(json(evidence.notes()))
                .createdAt(LocalDateTime.now())
                .build());

        IncidentContext context = loaded.context();
        eventPublisher.publishEvent(new AuditEvent(
                AuditAction.INCIDENT_ANALYZED, ANALYST_ACTOR, "DEPLOYMENT", deploymentId,
                context.applicationName(), context.environmentName(),
                "Déploiement #" + deploymentId + " : cause probable — " + diagnosis.title()
                        + " (" + CONFIDENCE_LABELS.get(diagnosis.confidence().name()) + ")",
                "Action proposée : " + diagnosis.action(), false));
        log.info("Analyse d'incident : déploiement #{} → {} ({})", deploymentId, diagnosis.code(), diagnosis.confidence());
        return Optional.of(toResponse(saved));
    }

    public Optional<IncidentAnalysisResponse> latest(Long deploymentId) {
        return analysisRepository.findFirstByDeploymentIdOrderByIdDesc(deploymentId).map(IncidentAnalysisService::toResponse);
    }

    private record Context(IncidentContext context, boolean failed) {
    }

    private static Context context(Deployment deployment) {
        String name = deployment.getKubernetesDeploymentName();
        return new Context(new IncidentContext(
                deployment.getId(),
                deployment.getVersion().getApplication().getName(),
                deployment.getEnvironment().getName(),
                deployment.getVersion().getVersion(),
                deployment.getEnvironment().getNamespace(),
                applicationPrefix(name, deployment.getVersion().getApplication().getName()),
                deployment.getJenkinsBuildNumber(),
                deployment.getErrorMessage()
        ), deployment.getStatus() == DeploymentStatus.FAILED);
    }

    /**
     * Préfixe des ressources Kubernetes. Si le déploiement a échoué avant
     * d'enregistrer son nom Kubernetes, il est déduit du nom de l'application,
     * selon la même convention (« fullstack-demo-backend » → « fullstack-demo »).
     */
    static String applicationPrefix(String kubernetesName, String applicationName) {
        if (kubernetesName != null && !kubernetesName.isBlank()) {
            return GuardQueries.applicationPrefix(kubernetesName);
        }
        if (applicationName == null || applicationName.isBlank()) {
            return null;
        }
        return applicationName.trim().toLowerCase().replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "");
    }

    static IncidentAnalysisResponse toResponse(IncidentAnalysis analysis) {
        return new IncidentAnalysisResponse(analysis.getId(), analysis.getDeploymentId(), analysis.getTrigger(),
                analysis.getCode(), analysis.getTitle(), analysis.getConfidence(), analysis.getExplanation(),
                analysis.getAction(), list(analysis.getEvidenceJson()), list(analysis.getAlternativesJson()),
                list(analysis.getNotesJson()), analysis.getCreatedAt());
    }

    private static String json(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private static List<String> list(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }
}
