package com.smartdeploy.portal;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.mail.MailService;
import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import com.smartdeploy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Discussion attachée à un déploiement : le développeur qui a fait la demande et les DevOps
 * (ou administrateurs) échangent sans quitter la plateforme. Chaque message prévient l'autre partie
 * par courrier électronique : un message du demandeur prévient les approbateurs, et inversement.
 */
@Service
public class DeploymentCommentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentCommentService.class);
    static final int MAX_LENGTH = 1000;
    static final Set<Role> TEAM = Set.of(Role.DEVOPS, Role.ADMIN);

    private final DeploymentCommentRepository repository;
    private final DeploymentRepository deploymentRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final String deploymentsUrl;

    public DeploymentCommentService(DeploymentCommentRepository repository, DeploymentRepository deploymentRepository,
                                    UserRepository userRepository, MailService mailService,
                                    @Value("${smartdeploy.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.repository = repository;
        this.deploymentRepository = deploymentRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.deploymentsUrl = frontendUrl.replaceAll("/+$", "") + "/deployments";
    }

    @Transactional(readOnly = true)
    public List<DeploymentCommentResponse> list(Long deploymentId, User reader) {
        Deployment deployment = deployment(deploymentId);
        ensureParticipant(deployment, reader);
        return repository.findByDeploymentIdOrderByCreatedAtAsc(deploymentId).stream()
                .map(c -> toResponse(c, reader))
                .toList();
    }

    @Transactional
    public DeploymentCommentResponse add(Long deploymentId, User author, String message) {
        Deployment deployment = deployment(deploymentId);
        ensureParticipant(deployment, author);
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Le message est vide");
        }
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Le message est limité à " + MAX_LENGTH + " caractères");
        }
        DeploymentComment saved = repository.save(DeploymentComment.builder()
                .deployment(deployment).author(author).message(text).createdAt(LocalDateTime.now()).build());
        notifyOthers(deployment, author, text);
        return toResponse(saved, author);
    }

    /** Participent à la discussion : le demandeur, les DevOps et les administrateurs. */
    static boolean isParticipant(Deployment deployment, User user) {
        if (user == null) {
            return false;
        }
        if (TEAM.contains(user.getRole())) {
            return true;
        }
        User requester = deployment.getRequestedBy();
        if (requester != null && Objects.equals(requester.getId(), user.getId())) {
            return true;
        }
        // Le propriétaire de l'application participe aussi, même s'il n'a pas fait la demande.
        User owner = owner(deployment);
        return owner != null && Objects.equals(owner.getId(), user.getId());
    }

    /** Propriétaire de l'application déployée, ou null. */
    static User owner(Deployment deployment) {
        return deployment.getVersion() == null || deployment.getVersion().getApplication() == null
                ? null : deployment.getVersion().getApplication().getOwner();
    }

    private void ensureParticipant(Deployment deployment, User user) {
        if (!isParticipant(deployment, user)) {
            throw new AccessDeniedException("Seuls le demandeur et l'équipe DevOps participent à cette discussion");
        }
    }

    /** Destinataires : les approbateurs si le demandeur écrit, sinon le demandeur. Jamais l'auteur. */
    List<User> recipients(Deployment deployment, User author) {
        User requester = deployment.getRequestedBy();
        boolean fromRequester = requester != null && Objects.equals(requester.getId(), author.getId());
        if (fromRequester) {
            return userRepository.findAllByDeletedFalse().stream()
                    .filter(User::isEnabled)
                    .filter(u -> TEAM.contains(u.getRole()))
                    .filter(u -> !Objects.equals(u.getId(), author.getId()))
                    .toList();
        }
        // L'équipe écrit : le demandeur et le propriétaire sont prévenus (jamais l'auteur, jamais deux fois).
        java.util.List<User> to = new java.util.ArrayList<>();
        if (requester != null && !Objects.equals(requester.getId(), author.getId())) {
            to.add(requester);
        }
        User owner = owner(deployment);
        if (owner != null && !Objects.equals(owner.getId(), author.getId())
                && (requester == null || !Objects.equals(owner.getId(), requester.getId()))) {
            to.add(owner);
        }
        return to;
    }

    private void notifyOthers(Deployment deployment, User author, String text) {
        String what = deployment.getVersion().getApplication().getName() + " " + deployment.getVersion().getVersion()
                + " sur " + deployment.getEnvironment().getName();
        for (User recipient : recipients(deployment, author)) {
            try {
                mailService.send(recipient.getEmail(), "SmartDeploy : nouveau message sur le déploiement #" + deployment.getId(),
                        "Bonjour,\n\n" + author.getEmail() + " a écrit à propos du déploiement #" + deployment.getId()
                                + " (" + what + ") :\n\n« " + text + " »\n\nRépondre : " + deploymentsUrl + "\n");
            } catch (RuntimeException exception) {
                log.warn("Discussion : courriel non envoyé à {} ({})", recipient.getEmail(), exception.getMessage());
            }
        }
    }

    private Deployment deployment(Long id) {
        return deploymentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new NoSuchElementException("Déploiement introuvable : " + id));
    }

    private static DeploymentCommentResponse toResponse(DeploymentComment c, User reader) {
        User a = c.getAuthor();
        String name = ((a.getFirstName() == null ? "" : a.getFirstName()) + " " + (a.getLastName() == null ? "" : a.getLastName())).trim();
        return new DeploymentCommentResponse(c.getId(), name.isEmpty() ? a.getEmail() : name, a.getEmail(),
                a.getRole() == null ? null : a.getRole().name(), c.getMessage(), c.getCreatedAt(),
                reader != null && Objects.equals(reader.getId(), a.getId()));
    }
}
