package com.smartdeploy.portal;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.incident.IncidentAnalysisRepository;
import com.smartdeploy.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/** Le fil d'un déploiement : ses messages et ses événements, dans l'ordre, comme une pull request. */
@RestController
public class DeploymentTimelineController {

    public record TimelineItem(String kind, LocalDateTime at, String text, String author, String authorRole, boolean mine, String tone) {
    }

    private final DeploymentCommentService comments;
    private final DeploymentRepository deployments;
    private final IncidentAnalysisRepository analyses;

    public DeploymentTimelineController(DeploymentCommentService comments, DeploymentRepository deployments,
                                        IncidentAnalysisRepository analyses) {
        this.comments = comments;
        this.deployments = deployments;
        this.analyses = analyses;
    }

    @GetMapping("/api/deployments/{deploymentId}/timeline")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
    @Transactional(readOnly = true)
    public List<TimelineItem> timeline(@PathVariable Long deploymentId, @AuthenticationPrincipal User user) {
        List<DeploymentCommentResponse> messages;
        try {
            messages = comments.list(deploymentId, user);      // vérifie aussi que l'utilisateur participe
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        Deployment d = deployments.findById(deploymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Déploiement introuvable"));
        List<TimelineItem> items = new ArrayList<>();
        for (DeploymentCommentResponse m : messages) {
            items.add(new TimelineItem("MESSAGE", m.createdAt(), m.message(), m.authorName(), m.authorRole(), m.mine(), null));
        }
        items.addAll(events(d));
        analyses.findFirstByDeploymentIdOrderByIdDesc(deploymentId).ifPresent(a ->
                items.add(event(a.getCreatedAt(), "Analyse : " + a.getTitle() + " (confiance " + a.getConfidence() + ")", "analysis")));
        items.sort(Comparator.comparing(TimelineItem::at, Comparator.nullsFirst(Comparator.naturalOrder())));
        return items;
    }

    static List<TimelineItem> events(Deployment d) {
        List<TimelineItem> items = new ArrayList<>();
        String who = d.getRequestedBy() == null ? "SmartDeploy" : d.getRequestedBy().getEmail();
        items.add(event(d.getCreatedAt(), "Demande créée par " + who + " : version " + d.getVersion().getVersion()
                + " vers " + d.getEnvironment().getName(), "neutral"));
        if (d.getApprovalDecidedAt() != null) {
            boolean refused = d.getStatus() == DeploymentStatus.REJECTED;
            items.add(event(d.getApprovalDecidedAt(), (refused ? "Refusée" : "Approuvée") + " par " + d.getApprovedBy()
                    + (d.getApprovalComment() == null || d.getApprovalComment().isBlank() ? "" : " : " + d.getApprovalComment()),
                    refused ? "critical" : "ok"));
        }
        if (d.getStartedAt() != null) {
            items.add(event(d.getStartedAt(), "Pipeline lancé", "neutral"));
        }
        if (d.getFinishedAt() != null) {
            String text = switch (d.getStatus()) {
                case SUCCESS -> "Déploiement réussi";
                case FAILED -> "Échec" + (d.getErrorMessage() == null ? "" : " : " + d.getErrorMessage());
                case CANCELLED -> "Déploiement annulé";
                default -> "Terminé (" + d.getStatus() + ")";
            };
            items.add(event(d.getFinishedAt(), text, d.getStatus() == DeploymentStatus.SUCCESS ? "ok"
                    : d.getStatus() == DeploymentStatus.FAILED ? "critical" : "neutral"));
        }
        return items;
    }

    private static TimelineItem event(LocalDateTime at, String text, String tone) {
        return new TimelineItem("EVENT", at, text, "SmartDeploy", null, false, tone);
    }
}
