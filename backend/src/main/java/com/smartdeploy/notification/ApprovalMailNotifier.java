package com.smartdeploy.notification;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditLog;
import com.smartdeploy.audit.AuditRecordedEvent;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.mail.MailService;
import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import com.smartdeploy.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * E-mails de l'approbation avant la production, déclenchés par le journal (comme Slack) :
 * les approbateurs à la demande ; la demandeuse à la décision ou à l'expiration.
 * Jamais d'e-mail à l'auteur de l'action ; un échec d'envoi ne bloque jamais la décision.
 */
@Component
public class ApprovalMailNotifier {

    static final Set<AuditAction> ACTIONS = EnumSet.of(AuditAction.DEPLOYMENT_APPROVAL_REQUESTED,
            AuditAction.DEPLOYMENT_APPROVED, AuditAction.DEPLOYMENT_REJECTED, AuditAction.DEPLOYMENT_APPROVAL_EXPIRED);
    private static final Set<Role> APPROVER_ROLES = EnumSet.of(Role.DEVOPS, Role.ADMIN);

    private final DeploymentRepository deploymentRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final String deploymentsUrl;

    public ApprovalMailNotifier(DeploymentRepository deploymentRepository, UserRepository userRepository, MailService mailService,
                                @Value("${smartdeploy.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.deploymentRepository = deploymentRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.deploymentsUrl = frontendUrl.replaceAll("/+$", "") + "/deployments";
    }

    @Async
    @EventListener
    @Transactional(readOnly = true)
    public void onAuditRecorded(AuditRecordedEvent event) {
        AuditLog entry = event.entry();
        if (entry == null || !ACTIONS.contains(entry.getAction()) || entry.getTargetId() == null) {
            return;
        }
        deploymentRepository.findById(entry.getTargetId()).ifPresent(deployment -> notify(entry.getAction(), deployment));
    }

    void notify(AuditAction action, Deployment deployment) {
        User requester = deployment.getRequestedBy();
        String what = deployment.getVersion().getApplication().getName() + " " + deployment.getVersion().getVersion()
                + " sur " + deployment.getEnvironment().getName();
        switch (action) {
            case DEPLOYMENT_APPROVAL_REQUESTED -> approvers(requester).forEach(approver -> mailService.send(approver.getEmail(),
                    "SmartDeploy : approbation demandée pour " + what,
                    "Bonjour,\n\n"
                            + (requester == null ? "Un utilisateur" : requester.getEmail()) + " demande à déployer " + what
                            + " (déploiement #" + deployment.getId() + ").\n"
                            + "Rien ne sera lancé avant la décision d'une seconde personne : votre décision est attendue.\n\n"
                            + "Examiner la demande : " + deploymentsUrl + "\n"));
            case DEPLOYMENT_APPROVED -> toRequester(requester, "SmartDeploy : votre déploiement a été approuvé",
                    "Votre déploiement #" + deployment.getId() + " (" + what + ") a été approuvé par " + deployment.getApprovedBy()
                            + (deployment.getApprovalComment() == null ? "" : " : « " + deployment.getApprovalComment() + " »")
                            + ".\nLe pipeline a démarré.");
            case DEPLOYMENT_REJECTED -> toRequester(requester, "SmartDeploy : votre déploiement a été refusé",
                    "Votre déploiement #" + deployment.getId() + " (" + what + ") a été refusé par " + deployment.getApprovedBy()
                            + " : « " + deployment.getApprovalComment() + " ».\nRien n'a été déployé.");
            case DEPLOYMENT_APPROVAL_EXPIRED -> toRequester(requester, "SmartDeploy : votre demande d'approbation a expiré",
                    "Votre demande de déploiement #" + deployment.getId() + " (" + what + ") a expiré sans réponse.\n"
                            + "Rien n'a été déployé : relancez la demande si elle est toujours d'actualité.");
            default -> { }
        }
    }

    /** DevOps et administrateurs actifs, sauf l'auteur de la demande (il ne peut pas décider). */
    List<User> approvers(User requester) {
        return userRepository.findAllByDeletedFalse().stream()
                .filter(User::isEnabled)
                .filter(user -> APPROVER_ROLES.contains(user.getRole()))
                .filter(user -> requester == null || !user.getId().equals(requester.getId()))
                .toList();
    }

    private void toRequester(User requester, String subject, String message) {
        if (requester != null && requester.isEnabled() && !requester.isDeleted()) {
            mailService.send(requester.getEmail(), subject,
                    "Bonjour,\n\n" + message + "\n\nVoir vos déploiements : " + deploymentsUrl + "\n");
        }
    }
}
