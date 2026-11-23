package com.smartdeploy.portal;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Messages non lus des discussions de déploiement : pour l'icône de messagerie et ses notifications.
 * Une discussion est lue jusqu'à la dernière ouverture de la personne ; ses propres messages ne comptent pas.
 */
@RestController
@PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
public class DiscussionUnreadController {

    /** Seuls les messages des 30 derniers jours sont pris en compte. */
    static final int WINDOW_DAYS = 30;

    public record UnreadThread(Long deploymentId, String applicationName, String version, String environmentName,
                               int unread, String lastMessage, String lastAuthor, LocalDateTime lastAt) {
    }

    public record Unread(int total, List<UnreadThread> threads) {
    }

    private final DeploymentCommentRepository comments;
    private final DeploymentCommentReadRepository reads;

    public DiscussionUnreadController(DeploymentCommentRepository comments, DeploymentCommentReadRepository reads) {
        this.comments = comments;
        this.reads = reads;
    }

    @GetMapping("/api/discussions/unread")
    @Transactional(readOnly = true)
    public Unread unread(@AuthenticationPrincipal User me) {
        Map<Long, LocalDateTime> lastRead = new HashMap<>();
        for (DeploymentCommentRead r : reads.findByUserId(me.getId())) {
            lastRead.put(r.getDeploymentId(), r.getLastReadAt());
        }
        Map<Long, List<DeploymentComment>> byDeployment = new LinkedHashMap<>();
        for (DeploymentComment c : comments.findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime.now().minusDays(WINDOW_DAYS))) {
            if (c.getAuthor() != null && Objects.equals(c.getAuthor().getId(), me.getId())) {
                continue;                                    // ses propres messages ne sont jamais « non lus »
            }
            Deployment d = c.getDeployment();
            if (!DeploymentCommentService.isParticipant(d, me)) {
                continue;                                    // seulement les discussions auxquelles on participe
            }
            LocalDateTime read = lastRead.get(d.getId());
            if (read != null && !c.getCreatedAt().isAfter(read)) {
                continue;
            }
            byDeployment.computeIfAbsent(d.getId(), k -> new ArrayList<>()).add(c);
        }
        List<UnreadThread> threads = new ArrayList<>();
        int total = 0;
        for (List<DeploymentComment> list : byDeployment.values()) {
            DeploymentComment last = list.get(0);            // le plus récent (tri décroissant)
            Deployment d = last.getDeployment();
            String text = last.getMessage();
            threads.add(new UnreadThread(d.getId(), d.getVersion().getApplication().getName(), d.getVersion().getVersion(),
                    d.getEnvironment().getName(), list.size(), text.length() > 90 ? text.substring(0, 90) + "…" : text,
                    last.getAuthor() == null ? "SmartDeploy" : name(last.getAuthor()), last.getCreatedAt()));
            total += list.size();
        }
        return new Unread(total, threads);
    }

    /** Toutes les conversations récentes (lues ou non), pour la messagerie de l'équipe. */
    @GetMapping("/api/discussions/recent")
    @Transactional(readOnly = true)
    public List<UnreadThread> recent(@AuthenticationPrincipal User me) {
        Map<Long, LocalDateTime> lastRead = new HashMap<>();
        for (DeploymentCommentRead r : reads.findByUserId(me.getId())) {
            lastRead.put(r.getDeploymentId(), r.getLastReadAt());
        }
        Map<Long, DeploymentComment> latest = new LinkedHashMap<>();
        Map<Long, Integer> unread = new HashMap<>();
        for (DeploymentComment c : comments.findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime.now().minusDays(WINDOW_DAYS))) {
            Deployment d = c.getDeployment();
            if (!DeploymentCommentService.isParticipant(d, me)) {
                continue;
            }
            latest.putIfAbsent(d.getId(), c);                 // le plus récent de chaque conversation
            boolean mine = c.getAuthor() != null && Objects.equals(c.getAuthor().getId(), me.getId());
            LocalDateTime read = lastRead.get(d.getId());
            if (!mine && (read == null || c.getCreatedAt().isAfter(read))) {
                unread.merge(d.getId(), 1, Integer::sum);
            }
        }
        List<UnreadThread> threads = new ArrayList<>();
        for (DeploymentComment last : latest.values()) {
            Deployment d = last.getDeployment();
            String text = last.getMessage();
            threads.add(new UnreadThread(d.getId(), d.getVersion().getApplication().getName(), d.getVersion().getVersion(),
                    d.getEnvironment().getName(), unread.getOrDefault(d.getId(), 0),
                    text.length() > 90 ? text.substring(0, 90) + "…" : text,
                    last.getAuthor() == null ? "SmartDeploy" : name(last.getAuthor()), last.getCreatedAt()));
            if (threads.size() == 30) {
                break;
            }
        }
        return threads;
    }

    /** Marque la discussion d'un déploiement comme lue, jusqu'à maintenant. */
    @PostMapping("/api/deployments/{deploymentId}/comments/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void markRead(@PathVariable Long deploymentId, @AuthenticationPrincipal User me) {
        DeploymentCommentRead r = reads.findByUserIdAndDeploymentId(me.getId(), deploymentId).orElseGet(() -> {
            DeploymentCommentRead created = new DeploymentCommentRead();
            created.setUserId(me.getId());
            created.setDeploymentId(deploymentId);
            return created;
        });
        r.setLastReadAt(LocalDateTime.now());
        reads.save(r);
    }

    private static String name(User u) {
        String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return n.isEmpty() ? u.getEmail() : n;
    }
}
