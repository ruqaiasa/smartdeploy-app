package com.smartdeploy.deployment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdeploy.integration.jenkins.JenkinsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Échecs silencieux de Jenkins : quand un build échoue AVANT d'avoir pu prévenir SmartDeploy (Jenkinsfile
 * illisible, Git ou Java en panne sur la machine…), le déploiement resterait « en attente ». Chaque minute,
 * SmartDeploy interroge lui-même Jenkins pour les déploiements en attente ou en cours, et applique l'échec
 * par le même chemin que le webhook (analyste, notifications, journal), avec la raison lue dans la console.
 */
@Component
public class JenkinsSilentFailureDetector {

    private static final Logger log = LoggerFactory.getLogger(JenkinsSilentFailureDetector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final Pattern QUEUE_ID = Pattern.compile("/queue/item/(\\d+)");
    /** On laisse une minute et demie à Jenkins pour démarrer et prévenir normalement. */
    static final int GRACE_SECONDS = 90;

    private final DeploymentRepository deployments;
    private final DeploymentService deploymentService;
    private final JenkinsClient jenkins;

    public JenkinsSilentFailureDetector(DeploymentRepository deployments, DeploymentService deploymentService,
                                        JenkinsClient jenkins) {
        this.deployments = deployments;
        this.deploymentService = deploymentService;
        this.jenkins = jenkins;
    }

    @Scheduled(initialDelay = 90_000, fixedDelay = 60_000)
    public void detect() {
        List<Deployment> open;
        try {
            open = deployments.findByStatusInAndDeletedFalse(List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING));
        } catch (RuntimeException exception) {
            return;
        }
        for (Deployment d : open) {
            try {
                check(d);
            } catch (RuntimeException exception) {
                log.debug("Échecs silencieux : vérification impossible pour #{} ({})", d.getId(), exception.getMessage());
            }
        }
    }

    private void check(Deployment d) {
        LocalDateTime since = d.getStartedAt() != null ? d.getStartedAt() : d.getCreatedAt();
        if (since == null || since.isAfter(LocalDateTime.now().minusSeconds(GRACE_SECONDS))) {
            return;
        }
        Integer build = d.getJenkinsBuildNumber();
        if (build == null) {
            Long queueId = queueId(d.getJenkinsQueueUrl());
            if (queueId == null) {
                return;                                   // pas encore transmis à Jenkins (sauvegarde en cours…)
            }
            String queueJson = jenkins.getQueueItemJson(queueId);
            if (queueJson == null) {
                return;                                   // élément expiré : le délai global du garde-fou prendra le relais
            }
            JsonNode item = read(queueJson);
            if (item.path("cancelled").asBoolean(false)) {
                fail(d, null, "Jenkins a annulé le build en file d'attente.");
                return;
            }
            build = item.path("executable").path("number").isInt() ? item.path("executable").path("number").asInt() : null;
            if (build == null) {
                return;                                   // toujours en file d'attente
            }
        }
        String buildJson = jenkins.getBuildJson(build);
        if (buildJson == null) {
            return;
        }
        JsonNode b = read(buildJson);
        String result = b.path("result").asText("");
        if (b.path("building").asBoolean(true) || !(result.equals("FAILURE") || result.equals("ABORTED") || result.equals("NOT_BUILT"))) {
            return;                                       // en cours, ou réussi (le webhook apporte les images)
        }
        String reason;
        try {
            reason = firstError(jenkins.getConsoleText(build));
        } catch (RuntimeException exception) {
            reason = null;
        }
        fail(d, build, "Jenkins a échoué sans prévenir SmartDeploy (build #" + build + ", " + result + ")"
                + (reason == null ? "." : " : " + reason));
    }

    private void fail(Deployment d, Integer build, String message) {
        deploymentService.applyPipelineCallback(d.getId(), DeploymentStatus.FAILED, build, null, message, null, null, null, null);
        log.warn("Échec silencieux de Jenkins détecté pour le déploiement #{} : {}", d.getId(), message);
    }

    static Long queueId(String queueUrl) {
        if (queueUrl == null) {
            return null;
        }
        Matcher m = QUEUE_ID.matcher(queueUrl);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    /** La première ligne significative de la console (erreur de compilation du Jenkinsfile, exception…). */
    static String firstError(String console) {
        if (console == null) {
            return null;
        }
        for (String line : console.split("\\r?\\n")) {
            String l = line.trim();
            if (l.startsWith("at ") || l.isEmpty()) {
                continue;
            }
            if (l.contains("startup failed") || l.contains("Exception") || l.startsWith("ERROR") || l.contains("unexpected char")
                    || l.contains("No such file") || l.contains("Permission denied")) {
                return l.length() > 220 ? l.substring(0, 220) + "…" : l;
            }
        }
        return null;
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Réponse de Jenkins illisible", exception);
        }
    }
}
