package com.smartdeploy.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentKubernetesConfig;
import com.smartdeploy.deployment.DeploymentKubernetesConfigRepository;
import com.smartdeploy.deployment.DeploymentReadAccess;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.integration.jenkins.JenkinsClient;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fiche de déploiement (portail développeur) : la progression réelle du pipeline, lue dans la console
 * Jenkins (chaque étape y commence par « [Pipeline] { (Nom) »), le temps restant estimé, l'adresse
 * de l'application, ses pods et leurs journaux, mots de passe masqués. Lecture limitée par RG-O1.
 */
@RestController
public class DeploymentProgressController {

    /** Les 32 étapes du Jenkinsfile, dans l'ordre. */
    static final List<String> STAGES = List.of("Notify RUNNING", "Deployment Information", "Validate Parameters",
            "Checkout Git", "Verify Checkout", "Verify Git Commit", "Build Maven", "Verify Artifact", "SonarQube Analysis",
            "Quality Gate", "Validate Dockerfile", "Prepare Deployment", "Docker Build", "Verify Docker Image",
            "Trivy Security Scan", "Docker Tag", "Check Docker Registry", "Docker Push", "Verify Registry",
            "Checkout Frontend", "Docker Build Frontend", "Trivy Frontend Security Scan", "Docker Push Frontend",
            "Prepare Kubernetes Images", "Check Kubernetes", "Prepare Kubernetes Namespace", "Download Kubernetes Manifests",
            "Validate Kubernetes Manifests", "Apply Kubernetes Manifests", "Verify Kubernetes Rollout",
            "Verify Kubernetes Resources", "Deployment Completed");

    record Phase(String key, String label, int from, int to) {
    }

    static final List<Phase> PHASES = List.of(
            new Phase("preparation", "Préparation", 0, 5),
            new Phase("qualite", "Construction et qualité", 6, 9),
            new Phase("serveur", "Image du serveur", 10, 18),
            new Phase("interface", "Image de l'interface", 19, 22),
            new Phase("deploiement", "Déploiement", 23, 31));

    public record PhaseState(String key, String label, String status) {
    }

    public record Progress(Long deploymentId, String status, String currentStage, int currentStageIndex, int totalStages,
                           List<PhaseState> phases, Long elapsedSeconds, Long estimatedRemainingSeconds, Long medianSeconds,
                           String applicationUrl, String errorMessage, String source, String pendingReason) {
    }

    public record PodSummary(String name, String component, boolean ready, int restarts, String phase, String lastTermination) {
    }

    public record LogExcerpt(String pod, boolean previous, List<String> lines, int totalLines) {
    }

    static final Pattern STAGE_MARK = Pattern.compile("\\[Pipeline\\] \\{ \\(([^)]+)\\)");
    static final Pattern SECRET = Pattern.compile(
            "(?i)((?:password|passwd|pwd|secret|token|api[_-]?key)[\"']?\\s*[=:]\\s*[\"']?)([^\\s\"',;]+)");
    static final Pattern ERROR_LINE = Pattern.compile("(?i)(error|exception|fatal|caused by|failed|\\sat [a-z][\\w.$]+\\()");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeploymentRepository deployments;
    private final DeploymentKubernetesConfigRepository configs;
    private final JenkinsClient jenkins;
    private final KubernetesClient kubernetes;
    private final DeploymentReadAccess readAccess;

    public DeploymentProgressController(DeploymentRepository deployments, DeploymentKubernetesConfigRepository configs,
                                        JenkinsClient jenkins, KubernetesClient kubernetes, DeploymentReadAccess readAccess) {
        this.deployments = deployments;
        this.configs = configs;
        this.jenkins = jenkins;
        this.kubernetes = kubernetes;
        this.readAccess = readAccess;
    }

    @GetMapping("/api/deployments/{id}/progress")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    @Transactional(readOnly = true)
    public Progress progress(@PathVariable Long id) {
        Deployment d = load(id);
        DeploymentStatus status = d.getStatus();
        int index = -1;
        String source = "none";
        if (status == DeploymentStatus.SUCCESS) {
            index = STAGES.size() - 1;
            source = "final";
        } else if ((status == DeploymentStatus.RUNNING || status == DeploymentStatus.FAILED
                || status == DeploymentStatus.CANCELLED) && d.getJenkinsBuildNumber() != null) {
            try {
                index = lastStageIndex(jenkins.getConsoleText(d.getJenkinsBuildNumber()));
                source = "jenkins";
            } catch (RuntimeException exception) {
                source = "unavailable";
            }
        }
        List<PhaseState> phases = phaseStates(index, status);
        Long elapsed = d.getStartedAt() == null ? null
                : Duration.between(d.getStartedAt(), d.getFinishedAt() != null ? d.getFinishedAt() : LocalDateTime.now()).getSeconds();
        Long median = medianSeconds(d);
        Long remaining = status == DeploymentStatus.RUNNING && median != null && elapsed != null ? Math.max(0, median - elapsed) : null;
        return new Progress(d.getId(), status.name(), index >= 0 ? STAGES.get(index) : null, index, STAGES.size(), phases,
                elapsed, remaining, median, applicationUrl(d.getId()), d.getErrorMessage(), source,
                status == DeploymentStatus.PENDING ? pendingReason(d) : null);
    }

    @GetMapping("/api/deployments/{id}/pods")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    @Transactional(readOnly = true)
    public List<PodSummary> pods(@PathVariable Long id) {
        Deployment d = load(id);
        try {
            return kubernetes.pods().inNamespace(d.getEnvironment().getNamespace()).list().getItems().stream()
                    .map(DeploymentProgressController::summary)
                    .sorted(Comparator.comparing(PodSummary::name))
                    .toList();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Le cluster est injoignable : " + exception.getMessage());
        }
    }

    @GetMapping("/api/deployments/{id}/logs")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    @Transactional(readOnly = true)
    public LogExcerpt logs(@PathVariable Long id, @RequestParam String pod,
                           @RequestParam(defaultValue = "false") boolean previous,
                           @RequestParam(defaultValue = "false") boolean errorsOnly) {
        Deployment d = load(id);
        String namespace = d.getEnvironment().getNamespace();
        try {
            Pod found = kubernetes.pods().inNamespace(namespace).withName(pod).get();
            if (found == null || found.getSpec() == null || found.getSpec().getContainers().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pod introuvable dans cet environnement : " + pod);
            }
            String container = found.getSpec().getContainers().get(0).getName();
            var resource = kubernetes.pods().inNamespace(namespace).withName(pod).inContainer(container);
            String raw = previous ? resource.terminated().tailingLines(400).getLog() : resource.tailingLines(400).getLog();
            List<String> lines = cleanLines(raw, errorsOnly);
            return new LogExcerpt(pod, previous, lines, lines.size());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    previous ? "Aucun journal précédent : ce conteneur n'a pas redémarré" : "Journaux indisponibles : " + exception.getMessage());
        }
    }

    /* ----------------------------------------------------------------------------------------- */

    private Deployment load(Long id) {
        readAccess.ensureCanRead(id);
        return deployments.findById(id).filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Déploiement introuvable : " + id));
    }

    static final Pattern QUEUE_ID = Pattern.compile("/queue/item/(\\d+)");

    /**
     * Pourquoi un déploiement accepté n'a pas encore démarré : le point de restauration est en cours,
     * ou Jenkins le garde dans sa file (avec la raison donnée par Jenkins lui-même).
     */
    String pendingReason(Deployment d) {
        if (d.getJenkinsQueueUrl() == null || d.getJenkinsQueueUrl().isBlank()) {
            return "Sauvegarde de la version en service avant le déploiement";
        }
        Matcher m = QUEUE_ID.matcher(d.getJenkinsQueueUrl());
        if (!m.find()) {
            return "En file d'attente de Jenkins";
        }
        try {
            String json = jenkins.getQueueItemJson(Long.parseLong(m.group(1)));
            if (json == null) {
                return "Jenkins a pris le build en charge : démarrage en cours";
            }
            JsonNode item = MAPPER.readTree(json);
            if (item.path("cancelled").asBoolean(false)) {
                return "Jenkins a annulé le build en file d'attente";
            }
            String why = item.path("why").asText("");
            return why.isBlank() ? "Transmis à Jenkins, démarrage imminent" : "Jenkins : " + why;
        } catch (Exception exception) {
            return "En file d'attente de Jenkins";
        }
    }

    /** L'étape la plus avancée atteinte, d'après les marqueurs de la console. */
    static int lastStageIndex(String console) {
        if (console == null) {
            return -1;
        }
        int index = -1;
        Matcher m = STAGE_MARK.matcher(console);
        while (m.find()) {
            int i = STAGES.indexOf(m.group(1).trim());
            if (i > index) {
                index = i;
            }
        }
        return index;
    }

    static List<PhaseState> phaseStates(int index, DeploymentStatus status) {
        boolean stopped = status == DeploymentStatus.FAILED || status == DeploymentStatus.CANCELLED;
        List<PhaseState> states = new ArrayList<>();
        for (Phase p : PHASES) {
            String state;
            if (status == DeploymentStatus.SUCCESS || index > p.to()) {
                state = "DONE";
            } else if (index >= p.from()) {
                state = stopped ? "FAILED" : "CURRENT";
            } else {
                state = "PENDING";
            }
            states.add(new PhaseState(p.key(), p.label(), state));
        }
        return states;
    }

    /** Médiane des déploiements réussis récents du même environnement (estimation affichée comme telle). */
    private Long medianSeconds(Deployment d) {
        List<Long> durations = deployments.findTop10ByEnvironment_IdAndDeletedFalseOrderByCreatedAtDesc(d.getEnvironment().getId()).stream()
                .filter(x -> x.getStatus() == DeploymentStatus.SUCCESS && x.getDurationSeconds() != null && !x.getId().equals(d.getId()))
                .map(Deployment::getDurationSeconds)
                .sorted()
                .toList();
        return durations.isEmpty() ? null : durations.get(durations.size() / 2);
    }

    private String applicationUrl(Long deploymentId) {
        return configs.findByDeploymentId(deploymentId)
                .map(DeploymentKubernetesConfig::getConfigurationJson)
                .map(DeploymentProgressController::hostOf)
                .orElse(null);
    }

    static String hostOf(String json) {
        try {
            JsonNode host = MAPPER.readTree(json).path("ingress").path("host");
            return host.isTextual() && !host.asText().isBlank() ? "http://" + host.asText() : null;
        } catch (Exception exception) {
            return null;
        }
    }

    static PodSummary summary(Pod pod) {
        List<ContainerStatus> cs = pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                ? List.of() : pod.getStatus().getContainerStatuses();
        boolean ready = !cs.isEmpty() && cs.stream().allMatch(c -> Boolean.TRUE.equals(c.getReady()));
        int restarts = cs.stream().mapToInt(c -> c.getRestartCount() == null ? 0 : c.getRestartCount()).sum();
        String termination = cs.stream()
                .map(c -> c.getLastState() == null || c.getLastState().getTerminated() == null ? null
                        : c.getLastState().getTerminated().getReason() + " (code " + c.getLastState().getTerminated().getExitCode() + ")")
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        String name = pod.getMetadata().getName();
        return new PodSummary(name, component(name), ready, restarts, pod.getStatus() == null ? null : pod.getStatus().getPhase(), termination);
    }

    static String component(String podName) {
        String n = podName.toLowerCase();
        if (n.contains("postgres")) return "Base de données";
        if (n.contains("frontend")) return "Interface";
        return "Serveur";
    }

    /** Masque les mots de passe et jetons ; garde au plus 200 lignes (les erreurs seulement, si demandé). */
    static List<String> cleanLines(String raw, boolean errorsOnly) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> lines = Arrays.stream(raw.split("\\r?\\n"))
                .map(line -> SECRET.matcher(line).replaceAll("$1••••"))
                .filter(line -> !errorsOnly || ERROR_LINE.matcher(line).find())
                .toList();
        return lines.size() > 200 ? lines.subList(lines.size() - 200, lines.size()) : lines;
    }
}
