package com.smartdeploy.portal;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.ApplicationAccess;
import com.smartdeploy.application.ApplicationRepository;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.incident.IncidentAnalysisRepository;
import com.smartdeploy.mail.MailService;
import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import com.smartdeploy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Réclamations : ouvertes par un développeur avec leur contexte, prises en charge par l'équipe DevOps,
 * renvoyées au demandeur pour précision, résolues avec leur cause, puis confirmées (ou rouvertes).
 * Une réclamation résolue sans réponse depuis 72 h est fermée automatiquement.
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);
    static final Set<Role> TEAM = Set.of(Role.DEVOPS, Role.ADMIN);

    public record CreateClaim(Claim.Type type, Claim.Priority priority, String title, String description,
                              Long applicationId, Long environmentId, Long deploymentId) {
    }

    public record ClaimResponse(Long id, String type, String priority, String status, String title, String description,
                                Long applicationId, String applicationName, String environmentName, Long deploymentId,
                                String requesterEmail, String assigneeEmail, String resolution, String cause, String context,
                                LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime firstResponseAt,
                                LocalDateTime resolvedAt, String waitingFor) {
    }

    public record ClaimMessageResponse(Long id, String authorName, String authorRole, String message, boolean systemEvent,
                                       LocalDateTime createdAt, boolean mine) {
    }

    private final ClaimRepository claims;
    private final ClaimMessageRepository messages;
    private final ApplicationRepository applications;
    private final EnvironmentRepository environments;
    private final DeploymentRepository deployments;
    private final IncidentAnalysisRepository analyses;
    private final UserRepository users;
    private final MailService mail;
    private final String claimsUrl;

    public ClaimService(ClaimRepository claims, ClaimMessageRepository messages, ApplicationRepository applications,
                        EnvironmentRepository environments, DeploymentRepository deployments,
                        IncidentAnalysisRepository analyses, UserRepository users, MailService mail,
                        @Value("${smartdeploy.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.claims = claims;
        this.messages = messages;
        this.applications = applications;
        this.environments = environments;
        this.deployments = deployments;
        this.analyses = analyses;
        this.users = users;
        this.mail = mail;
        this.claimsUrl = frontendUrl.replaceAll("/+$", "") + "/claims/";
    }

    /* ----------------------------------------------------------------- lecture */

    @Transactional(readOnly = true)
    public List<ClaimResponse> list(User user) {
        List<Claim> visible = isTeam(user) ? claims.findAllByOrderByCreatedAtDesc() : claims.findVisibleTo(user.getId());
        return visible.stream().map(ClaimService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(Long id, User user) {
        return toResponse(visible(id, user));
    }

    @Transactional(readOnly = true)
    public List<ClaimMessageResponse> thread(Long id, User user) {
        visible(id, user);
        return messages.findByClaimIdOrderByCreatedAtAsc(id).stream().map(m -> toResponse(m, user)).toList();
    }

    /* ----------------------------------------------------------------- écriture */

    @Transactional
    public ClaimResponse create(CreateClaim request, User user) {
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Le titre est obligatoire");
        }
        Application application = applications.findByIdAndDeletedFalse(request.applicationId())
                .orElseThrow(() -> new NoSuchElementException("Application introuvable"));
        ApplicationAccess.ensureCanAccess(application, user);
        Environment environment = request.environmentId() == null ? null : environments.findByIdAndDeletedFalse(request.environmentId())
                .filter(e -> Objects.equals(e.getApplication().getId(), application.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Cet environnement n'appartient pas à l'application"));
        Deployment deployment = request.deploymentId() == null ? null : deployments.findById(request.deploymentId())
                .filter(d -> Objects.equals(d.getVersion().getApplication().getId(), application.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Ce déploiement n'appartient pas à l'application"));
        if (environment == null && deployment != null) {
            environment = deployment.getEnvironment();
        }
        Claim claim = new Claim();
        claim.setType(request.type() == null ? Claim.Type.QUESTION : request.type());
        claim.setPriority(request.priority() == null ? Claim.Priority.NORMAL : request.priority());
        claim.setStatus(Claim.Status.OPEN);
        claim.setTitle(cut(request.title().trim(), 200));
        claim.setDescription(request.description() == null ? null : cut(request.description().trim(), 4000));
        claim.setApplication(application);
        claim.setEnvironment(environment);
        claim.setDeployment(deployment);
        claim.setRequester(user);
        claim.setContext(cut(context(application, environment, deployment), 4000));
        LocalDateTime now = LocalDateTime.now();
        claim.setCreatedAt(now);
        claim.setUpdatedAt(now);
        Claim saved = claims.save(claim);
        event(saved, user, "Réclamation ouverte par " + user.getEmail());
        notifyTeam(saved, user, "Nouvelle réclamation (" + label(saved.getPriority()) + ") : " + saved.getTitle());
        return toResponse(saved);
    }

    @Transactional
    public ClaimMessageResponse addMessage(Long id, User user, String text, boolean askRequester) {
        Claim claim = visible(id, user);
        String message = text == null ? "" : text.trim();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("Le message est vide");
        }
        if (claim.getStatus() == Claim.Status.CLOSED) {
            throw new IllegalStateException("Cette réclamation est fermée");
        }
        boolean team = isTeam(user);
        if (team && claim.getFirstResponseAt() == null) {
            claim.setFirstResponseAt(LocalDateTime.now());
        }
        if (team && askRequester) {
            claim.setStatus(Claim.Status.WAITING_REQUESTER);
        } else if (!team && claim.getStatus() == Claim.Status.WAITING_REQUESTER) {
            claim.setStatus(Claim.Status.IN_PROGRESS);
        }
        claim.setUpdatedAt(LocalDateTime.now());
        ClaimMessage saved = save(claim, user, cut(message, 2000), false);
        if (team) {
            notifyRequester(claim, user, (askRequester ? "Une précision vous est demandée" : "Nouveau message") + " : " + message);
        } else {
            notifyTeam(claim, user, "Nouveau message du demandeur : " + message);
        }
        return toResponse(saved, user);
    }

    @Transactional
    public ClaimResponse assign(Long id, User user) {
        Claim claim = teamOnly(id, user);
        claim.setAssignee(user);
        if (claim.getStatus() == Claim.Status.OPEN) {
            claim.setStatus(Claim.Status.IN_PROGRESS);
        }
        if (claim.getFirstResponseAt() == null) {
            claim.setFirstResponseAt(LocalDateTime.now());
        }
        claim.setUpdatedAt(LocalDateTime.now());
        event(claim, user, "Prise en charge par " + user.getEmail());
        notifyRequester(claim, user, "Votre réclamation est prise en charge par " + user.getEmail());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse resolve(Long id, User user, String resolution, Claim.Cause cause) {
        Claim claim = teamOnly(id, user);
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("Décrivez la solution apportée");
        }
        if (claim.getStatus() == Claim.Status.CLOSED) {
            throw new IllegalStateException("Cette réclamation est déjà fermée");
        }
        LocalDateTime now = LocalDateTime.now();
        claim.setResolution(cut(resolution.trim(), 4000));
        claim.setCause(cause);
        claim.setStatus(Claim.Status.RESOLVED);
        claim.setResolvedAt(now);
        if (claim.getAssignee() == null) {
            claim.setAssignee(user);
        }
        if (claim.getFirstResponseAt() == null) {
            claim.setFirstResponseAt(now);
        }
        claim.setUpdatedAt(now);
        event(claim, user, "Résolue par " + user.getEmail() + (cause == null ? "" : " (cause : " + label(cause) + ")") + " : " + claim.getResolution());
        notifyRequester(claim, user, "Votre réclamation est résolue : " + claim.getResolution()
                + "\nConfirmez la résolution, ou rouvrez la réclamation si le problème persiste.");
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse confirm(Long id, User user) {
        Claim claim = requesterSide(id, user);
        if (claim.getStatus() != Claim.Status.RESOLVED) {
            throw new IllegalStateException("Seule une réclamation résolue peut être confirmée");
        }
        claim.setStatus(Claim.Status.CLOSED);
        claim.setClosedAt(LocalDateTime.now());
        claim.setUpdatedAt(LocalDateTime.now());
        event(claim, user, "Résolution confirmée par " + user.getEmail() + " : réclamation fermée");
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse reopen(Long id, User user, String reason) {
        Claim claim = requesterSide(id, user);
        if (claim.getStatus() != Claim.Status.RESOLVED) {
            throw new IllegalStateException("Seule une réclamation résolue peut être rouverte");
        }
        claim.setStatus(Claim.Status.IN_PROGRESS);
        claim.setResolvedAt(null);
        claim.setUpdatedAt(LocalDateTime.now());
        event(claim, user, "Rouverte par " + user.getEmail() + (reason == null || reason.isBlank() ? "" : " : " + reason.trim()));
        notifyTeam(claim, user, "Réclamation rouverte : " + claim.getTitle());
        return toResponse(claim);
    }

    /** Une réclamation résolue sans réponse du demandeur depuis 72 h est fermée. */
    @Scheduled(initialDelay = 120_000, fixedDelay = 3_600_000)
    @Transactional
    public void closeForgottenResolutions() {
        for (Claim claim : claims.findByStatusAndResolvedAtBefore(Claim.Status.RESOLVED, LocalDateTime.now().minusHours(72))) {
            claim.setStatus(Claim.Status.CLOSED);
            claim.setClosedAt(LocalDateTime.now());
            event(claim, null, "Fermée automatiquement : aucune réponse du demandeur pendant 72 h");
        }
    }

    /* ----------------------------------------------------------------- règles d'accès */

    static boolean isTeam(User user) {
        return user != null && TEAM.contains(user.getRole());
    }

    /** Participent : l'équipe DevOps, le demandeur, et le propriétaire de l'application. */
    static boolean canSee(Claim claim, User user) {
        if (user == null) {
            return false;
        }
        if (isTeam(user)) {
            return true;
        }
        boolean requester = claim.getRequester() != null && Objects.equals(claim.getRequester().getId(), user.getId());
        User owner = claim.getApplication() == null ? null : claim.getApplication().getOwner();
        return requester || (owner != null && Objects.equals(owner.getId(), user.getId()));
    }

    private Claim load(Long id) {
        return claims.findById(id).orElseThrow(() -> new NoSuchElementException("Réclamation introuvable : " + id));
    }

    private Claim visible(Long id, User user) {
        Claim claim = load(id);
        if (!canSee(claim, user)) {
            throw new AccessDeniedException("Cette réclamation ne vous concerne pas");
        }
        return claim;
    }

    private Claim teamOnly(Long id, User user) {
        if (!isTeam(user)) {
            throw new AccessDeniedException("Action réservée à l'équipe DevOps");
        }
        return load(id);
    }

    private Claim requesterSide(Long id, User user) {
        Claim claim = visible(id, user);
        if (isTeam(user) && !Objects.equals(claim.getRequester().getId(), user.getId())) {
            throw new AccessDeniedException("Seul le demandeur confirme ou rouvre sa réclamation");
        }
        return claim;
    }

    /* ----------------------------------------------------------------- contexte et fil */

    /** Le contexte joint automatiquement : de quoi éviter au DevOps de redemander. */
    String context(Application application, Environment environment, Deployment deployment) {
        List<String> lines = new ArrayList<>();
        lines.add("Application : " + application.getName() + " (" + application.getRepositoryUrl() + ")");
        if (environment != null) {
            lines.add("Environnement : " + environment.getName() + " (" + environment.getNamespace() + ")");
        }
        if (deployment != null) {
            lines.add("Déploiement #" + deployment.getId() + " : version " + deployment.getVersion().getVersion()
                    + ", statut " + deployment.getStatus() + (deployment.getErrorMessage() == null ? "" : ", erreur : " + deployment.getErrorMessage()));
            analyses.findFirstByDeploymentIdOrderByIdDesc(deployment.getId()).ifPresent(a ->
                    lines.add("Analyse : " + a.getTitle() + " (confiance " + a.getConfidence() + ") — " + a.getAction()));
        }
        return String.join("\n", lines);
    }

    private void event(Claim claim, User author, String text) {
        save(claim, author, cut(text, 2000), true);
    }

    private ClaimMessage save(Claim claim, User author, String text, boolean systemEvent) {
        ClaimMessage m = new ClaimMessage();
        m.setClaim(claim);
        m.setAuthor(author);
        m.setMessage(text);
        m.setSystemEvent(systemEvent);
        m.setCreatedAt(LocalDateTime.now());
        return messages.save(m);
    }

    /* ----------------------------------------------------------------- notifications */

    private void notifyTeam(Claim claim, User author, String text) {
        Set<String> to = new LinkedHashSet<>();
        if (claim.getAssignee() != null) {
            to.add(claim.getAssignee().getEmail());
        } else {
            users.findAllByDeletedFalse().stream().filter(User::isEnabled).filter(ClaimService::isTeam)
                    .forEach(u -> to.add(u.getEmail()));
        }
        if (author != null) {
            to.remove(author.getEmail());
        }
        send(to, claim, text);
    }

    private void notifyRequester(Claim claim, User author, String text) {
        Set<String> to = new LinkedHashSet<>();
        to.add(claim.getRequester().getEmail());
        User owner = claim.getApplication().getOwner();
        if (owner != null) {
            to.add(owner.getEmail());
        }
        if (author != null) {
            to.remove(author.getEmail());
        }
        send(to, claim, text);
    }

    private void send(Set<String> to, Claim claim, String text) {
        for (String address : to) {
            try {
                mail.send(address, "SmartDeploy : réclamation #" + claim.getId() + " — " + claim.getTitle(),
                        "Bonjour,\n\n" + text + "\n\nRéclamation : " + claimsUrl + claim.getId() + "\n");
            } catch (RuntimeException exception) {
                log.warn("Réclamation #{} : courriel non envoyé à {} ({})", claim.getId(), address, exception.getMessage());
            }
        }
    }

    /* ----------------------------------------------------------------- présentation */

    static String label(Claim.Priority priority) {
        return switch (priority) {
            case CRITICAL -> "critique";
            case HIGH -> "haute";
            default -> "normale";
        };
    }

    static String label(Claim.Cause cause) {
        return switch (cause) {
            case CODE -> "code";
            case CONFIGURATION -> "configuration";
            case RESOURCES -> "ressources";
            case PLATFORM -> "plateforme";
            case DEPENDENCY -> "dépendance";
            default -> "utilisation";
        };
    }

    /** Qui doit agir : l'équipe, le demandeur, ou personne. */
    static String waitingFor(Claim.Status status) {
        return switch (status) {
            case OPEN, IN_PROGRESS -> "TEAM";
            case WAITING_REQUESTER, RESOLVED -> "REQUESTER";
            default -> null;
        };
    }

    static ClaimResponse toResponse(Claim c) {
        return new ClaimResponse(c.getId(), c.getType().name(), c.getPriority().name(), c.getStatus().name(), c.getTitle(),
                c.getDescription(), c.getApplication().getId(), c.getApplication().getName(),
                c.getEnvironment() == null ? null : c.getEnvironment().getName(),
                c.getDeployment() == null ? null : c.getDeployment().getId(),
                c.getRequester().getEmail(), c.getAssignee() == null ? null : c.getAssignee().getEmail(),
                c.getResolution(), c.getCause() == null ? null : c.getCause().name(), c.getContext(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getFirstResponseAt(), c.getResolvedAt(), waitingFor(c.getStatus()));
    }

    static ClaimMessageResponse toResponse(ClaimMessage m, User reader) {
        User a = m.getAuthor();
        String name = a == null ? "SmartDeploy"
                : ((a.getFirstName() == null ? "" : a.getFirstName()) + " " + (a.getLastName() == null ? "" : a.getLastName())).trim();
        return new ClaimMessageResponse(m.getId(), a != null && name.isEmpty() ? a.getEmail() : name,
                a == null || a.getRole() == null ? null : a.getRole().name(), m.getMessage(), m.isSystemEvent(), m.getCreatedAt(),
                a != null && reader != null && Objects.equals(a.getId(), reader.getId()));
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
