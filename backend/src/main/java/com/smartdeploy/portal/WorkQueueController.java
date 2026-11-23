package com.smartdeploy.portal;

import com.smartdeploy.alert.Alert;
import com.smartdeploy.alert.AlertRepository;
import com.smartdeploy.alert.AlertSeverity;
import com.smartdeploy.alert.AlertStatus;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.EnvironmentType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Console DevOps : une file de travail unique, triée par urgence — approbations (la production d'abord),
 * alertes ouvertes, échecs des dernières 24 h.
 */
@RestController
public class WorkQueueController {

    public record QueueItem(String kind, int urgency, String title, String detail, String applicationName,
                            String environmentName, String environmentType, Long deploymentId, String requester,
                            LocalDateTime since, String link) {
    }

    public record WorkQueue(int approvals, int alerts, int failures, int claims, List<QueueItem> items) {
    }

    private final DeploymentRepository deployments;
    private final AlertRepository alerts;
    private final ClaimRepository claims;
    private final com.smartdeploy.user.UserRepository users;

    public WorkQueueController(DeploymentRepository deployments, AlertRepository alerts, ClaimRepository claims,
                               com.smartdeploy.user.UserRepository users) {
        this.deployments = deployments;
        this.alerts = alerts;
        this.claims = claims;
        this.users = users;
    }

    @GetMapping("/api/portal/queue")
    @PreAuthorize("hasAnyRole('DEVOPS', 'ADMIN')")
    @Transactional(readOnly = true)
    public WorkQueue queue(@org.springframework.security.core.annotation.AuthenticationPrincipal com.smartdeploy.user.User me) {
        List<QueueItem> items = new ArrayList<>();
        int approvals = 0;
        for (Deployment d : deployments.findByStatusInAndDeletedFalse(List.of(DeploymentStatus.AWAITING_APPROVAL))) {
            boolean production = d.getEnvironment().getType() == EnvironmentType.PRODUCTION;
            boolean promotion = d.getImagesFrom() != null;
            boolean fromDeveloper = d.getRequestedBy() != null && d.getRequestedBy().getRole() == com.smartdeploy.user.Role.DEVELOPER;
            String title = promotion
                    ? (production ? "Promotion vers la production à réaliser" : "Promotion à réaliser")
                    : (production ? "Mise en production à approuver" : "Demande de déploiement à approuver");
            String origin = promotion ? " : de " + d.getImagesFrom().getEnvironment().getName() + " vers " + d.getEnvironment().getName()
                    + " (mêmes images)" : " vers " + d.getEnvironment().getName();
            items.add(new QueueItem("APPROVAL", production ? 100 : 70,
                    title + (fromDeveloper ? ", demandée par le développeur" : ""),
                    d.getVersion().getApplication().getName() + " " + d.getVersion().getVersion() + origin
                            + (d.getRequestMessage() == null ? "" : " : « " + d.getRequestMessage() + " »"),
                    d.getVersion().getApplication().getName(), d.getEnvironment().getName(), d.getEnvironment().getType().name(),
                    d.getId(), d.getRequestedBy() == null ? null : d.getRequestedBy().getEmail(), d.getCreatedAt(),
                    "/deployments/" + d.getId()));
            approvals++;
        }
        int alertCount = 0;
        for (Alert a : alerts.findByStatusOrderByCreatedAtDesc(AlertStatus.OPEN)) {
            items.add(new QueueItem("ALERT", a.getSeverity() == AlertSeverity.CRITICAL ? 90 : 40, a.getTitle(), a.getMessage(),
                    a.getApplication() == null ? null : a.getApplication().getName(),
                    a.getEnvironment() == null ? null : a.getEnvironment().getName(),
                    a.getEnvironment() == null || a.getEnvironment().getType() == null ? null : a.getEnvironment().getType().name(),
                    a.getDeployment() == null ? null : a.getDeployment().getId(), null, a.getCreatedAt(), "/alerts"));
            alertCount++;
        }
        int failures = 0;
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        for (Deployment d : deployments.findByStatusInAndDeletedFalse(List.of(DeploymentStatus.FAILED))) {
            if (d.getFinishedAt() == null || d.getFinishedAt().isBefore(since)) {
                continue;
            }
            String error = d.getErrorMessage() == null ? "" : d.getErrorMessage();
            items.add(new QueueItem("FAILURE", 50, "Déploiement échoué",
                    d.getVersion().getApplication().getName() + " " + d.getVersion().getVersion() + " sur " + d.getEnvironment().getName()
                            + (error.isBlank() ? "" : " : " + (error.length() > 140 ? error.substring(0, 140) + "…" : error)),
                    d.getVersion().getApplication().getName(), d.getEnvironment().getName(), d.getEnvironment().getType().name(),
                    d.getId(), d.getRequestedBy() == null ? null : d.getRequestedBy().getEmail(), d.getFinishedAt(),
                    "/deployments/" + d.getId()));
            failures++;
        }
        int claimCount = 0;
        for (Claim c : claims.findByStatusIn(List.of(Claim.Status.OPEN, Claim.Status.IN_PROGRESS))) {
            int urgency = c.getPriority() == Claim.Priority.CRITICAL ? 95 : c.getPriority() == Claim.Priority.HIGH ? 65 : 45;
            items.add(new QueueItem("CLAIM", urgency, "Réclamation : " + c.getTitle(),
                    (c.getAssignee() == null ? "Non prise en charge" : "Prise en charge par " + c.getAssignee().getEmail())
                            + " · " + c.getApplication().getName() + (c.getEnvironment() == null ? "" : " · " + c.getEnvironment().getName()),
                    c.getApplication().getName(), c.getEnvironment() == null ? null : c.getEnvironment().getName(),
                    c.getEnvironment() == null || c.getEnvironment().getType() == null ? null : c.getEnvironment().getType().name(),
                    c.getDeployment() == null ? null : c.getDeployment().getId(), c.getRequester().getEmail(), c.getCreatedAt(),
                    "/claims/" + c.getId()));
            claimCount++;
        }
        // RG-U1 : les inscriptions à valider, pour l'administrateur
        if (me != null && me.getRole() == com.smartdeploy.user.Role.ADMIN) {
            for (com.smartdeploy.user.User u : users.findAllByDeletedFalse()) {
                if (!u.isPendingApproval()) {
                    continue;
                }
                items.add(new QueueItem("ACCOUNT", 60, "Compte à valider : " + u.getFirstName() + " " + u.getLastName(),
                        u.getEmail() + (u.getAccessRequest() == null ? "" : " : « " + u.getAccessRequest() + " »"),
                        null, null, null, null, u.getEmail(), u.getCreatedAt(), "/users"));
            }
        }
        items.sort(Comparator.comparingInt(QueueItem::urgency).reversed()
                .thenComparing(QueueItem::since, Comparator.nullsLast(Comparator.naturalOrder())));
        return new WorkQueue(approvals, alertCount, failures, claimCount, items);
    }
}
