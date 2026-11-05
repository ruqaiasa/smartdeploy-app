package com.smartdeploy.incident;

import com.smartdeploy.guard.DeploymentGuardResponse;
import com.smartdeploy.guard.DeploymentGuardService;
import com.smartdeploy.guard.GuardCheck;
import com.smartdeploy.guard.GuardLevel;
import com.smartdeploy.integration.jenkins.JenkinsClient;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rassemble les preuves d'un incident : log Jenkins, état des pods,
 * événements Kubernetes, logs des conteneurs en difficulté et contrôles du
 * gardien. Chaque source est indépendante : une source indisponible est
 * signalée dans les notes, sans empêcher l'analyse.
 */
@Component
public class IncidentEvidenceCollector {

    static final int JENKINS_TAIL_LINES = 300;
    static final int CONTAINER_TAIL_LINES = 80;
    static final int MAX_EVENTS = 12;
    static final int MAX_LOGGED_PODS = 2;

    private static final Pattern STAGE = Pattern.compile("«\\s*(.+?)\\s*»");
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*[A-Za-z]");

    private final JenkinsClient jenkinsClient;
    private final KubernetesClient kubernetesClient;
    private final DeploymentGuardService guardService;

    public IncidentEvidenceCollector(JenkinsClient jenkinsClient, KubernetesClient kubernetesClient,
                                     DeploymentGuardService guardService) {
        this.jenkinsClient = jenkinsClient;
        this.kubernetesClient = kubernetesClient;
        this.guardService = guardService;
    }

    public IncidentEvidence collect(IncidentContext context, IncidentTrigger trigger) {
        List<String> notes = new ArrayList<>();
        List<String> jenkins = trigger == IncidentTrigger.PIPELINE ? jenkinsLog(context, notes) : List.of();
        List<String> pods = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        kubernetes(context, pods, logs, notes);
        List<String> events = events(context, notes);
        List<String> guard = trigger == IncidentTrigger.GUARD ? guardChecks(context) : List.of();

        return new IncidentEvidence(failedStage(context.errorMessage()), context.errorMessage(),
                jenkins, pods, events, logs, guard, notes);
    }

    /** « Échec à l'étape « Build Maven ». … » → « Build Maven ». */
    static String failedStage(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        Matcher matcher = STAGE.matcher(errorMessage);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Lignes utiles d'un log : sans codes de couleur ni lignes techniques du pipeline. */
    static List<String> clean(String text, int tail) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> lines = Arrays.stream(text.split("\\R"))
                .map(line -> ANSI.matcher(line).replaceAll("").stripTrailing())
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("[Pipeline]"))
                .toList();
        return lines.subList(Math.max(0, lines.size() - tail), lines.size());
    }

    private List<String> jenkinsLog(IncidentContext context, List<String> notes) {
        if (context.jenkinsBuildNumber() == null) {
            notes.add("Log Jenkins : aucun numéro de build (le pipeline n'a pas démarré).");
            return List.of();
        }
        try {
            return clean(jenkinsClient.getConsoleText(context.jenkinsBuildNumber()), JENKINS_TAIL_LINES);
        } catch (RuntimeException exception) {
            notes.add("Log Jenkins indisponible : " + exception.getMessage());
            return List.of();
        }
    }

    private boolean belongsToApplication(String name, String prefix) {
        return name != null && (name.startsWith(prefix + "-") || name.matches("postgresql-\\d+"));
    }

    private void kubernetes(IncidentContext context, List<String> podLines, List<String> logLines, List<String> notes) {
        if (context.namespace() == null || context.applicationPrefix() == null) {
            notes.add("Kubernetes : espace de noms inconnu.");
            return;
        }
        List<Pod> pods;
        try {
            pods = kubernetesClient.pods().inNamespace(context.namespace()).list().getItems().stream()
                    .filter(pod -> belongsToApplication(pod.getMetadata().getName(), context.applicationPrefix()))
                    .toList();
        } catch (KubernetesClientException exception) {
            notes.add("État des pods indisponible : " + reason(exception));
            return;
        }

        List<String[]> troubled = new ArrayList<>();
        for (Pod pod : pods) {
            List<ContainerStatus> statuses = pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                    ? List.of() : pod.getStatus().getContainerStatuses();
            for (ContainerStatus status : statuses) {
                podLines.add(describe(pod.getMetadata().getName(), status));
                boolean waitingBadly = status.getState() != null && status.getState().getWaiting() != null;
                int restarts = status.getRestartCount() == null ? 0 : status.getRestartCount();
                if (restarts > 0 || waitingBadly) {
                    troubled.add(new String[] {pod.getMetadata().getName(), status.getName(), String.valueOf(restarts)});
                }
            }
        }

        troubled.sort(Comparator.comparingInt((String[] t) -> Integer.parseInt(t[2])).reversed());
        for (String[] container : troubled.stream().limit(MAX_LOGGED_PODS).toList()) {
            logLines.addAll(containerLog(context.namespace(), container[0], container[1], Integer.parseInt(container[2]) > 0, notes));
        }
    }

    static String describe(String podName, ContainerStatus status) {
        StringBuilder line = new StringBuilder(podName).append('/').append(status.getName()).append(" : ");
        if (status.getState() != null && status.getState().getWaiting() != null) {
            line.append(status.getState().getWaiting().getReason());
        } else if (status.getState() != null && status.getState().getTerminated() != null) {
            line.append("Terminated ").append(status.getState().getTerminated().getReason());
        } else {
            line.append(Boolean.TRUE.equals(status.getReady()) ? "Running, prêt" : "Running, non prêt");
        }
        if (status.getLastState() != null && status.getLastState().getTerminated() != null) {
            var last = status.getLastState().getTerminated();
            line.append(" ; dernier arrêt : ").append(last.getReason())
                    .append(" (exit code ").append(last.getExitCode()).append(')');
        }
        line.append(" ; ").append(status.getRestartCount() == null ? 0 : status.getRestartCount()).append(" redémarrage(s)");
        return line.toString();
    }

    private List<String> containerLog(String namespace, String pod, String container, boolean previous, List<String> notes) {
        try {
            var resource = kubernetesClient.pods().inNamespace(namespace).withName(pod).inContainer(container);
            String log = previous
                    ? resource.terminated().tailingLines(CONTAINER_TAIL_LINES).getLog()
                    : resource.tailingLines(CONTAINER_TAIL_LINES).getLog();
            return clean(log, CONTAINER_TAIL_LINES);
        } catch (KubernetesClientException exception) {
            notes.add("Log du conteneur " + pod + " indisponible : " + reason(exception));
            return List.of();
        }
    }

    private List<String> events(IncidentContext context, List<String> notes) {
        if (context.namespace() == null || context.applicationPrefix() == null) {
            return List.of();
        }
        try {
            return kubernetesClient.v1().events().inNamespace(context.namespace()).list().getItems().stream()
                    .filter(event -> "Warning".equals(event.getType()))
                    .filter(event -> event.getInvolvedObject() != null
                            && (belongsToApplication(event.getInvolvedObject().getName(), context.applicationPrefix())
                            || Objects.equals(event.getInvolvedObject().getName(), context.applicationPrefix() + "-backend")
                            || Objects.equals(event.getInvolvedObject().getName(), context.applicationPrefix() + "-frontend")))
                    .sorted(Comparator.comparing(IncidentEvidenceCollector::timestamp).reversed())
                    .limit(MAX_EVENTS)
                    .map(event -> "Warning " + event.getReason() + " " + event.getInvolvedObject().getKind().toLowerCase()
                            + "/" + event.getInvolvedObject().getName() + " : " + event.getMessage()
                            + (event.getCount() != null && event.getCount() > 1 ? " (×" + event.getCount() + ")" : ""))
                    .toList();
        } catch (KubernetesClientException exception) {
            notes.add("Événements Kubernetes indisponibles : " + reason(exception));
            return List.of();
        }
    }

    private static String timestamp(Event event) {
        String value = event.getLastTimestamp() != null ? event.getLastTimestamp() : event.getEventTime() != null
                ? event.getEventTime().getTime() : event.getMetadata().getCreationTimestamp();
        return value == null ? "" : value;
    }

    private List<String> guardChecks(IncidentContext context) {
        return guardService.findForDeployment(context.deploymentId())
                .map(DeploymentGuardResponse::checks)
                .orElse(List.of())
                .stream()
                .filter(check -> check.level() == GuardLevel.CRITICAL || check.level() == GuardLevel.WARNING)
                .map(IncidentEvidenceCollector::describe)
                .toList();
    }

    private static String describe(GuardCheck check) {
        return check.label() + " : " + check.observed()
                + (check.reference() == null ? "" : " (référence : " + check.reference() + ")");
    }

    private static String reason(KubernetesClientException exception) {
        return exception.getCode() == 403
                ? "accès refusé (droits du compte de service SmartDeploy à compléter)"
                : exception.getMessage();
    }
}
