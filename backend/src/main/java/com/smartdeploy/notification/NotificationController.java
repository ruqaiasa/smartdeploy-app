package com.smartdeploy.notification;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Message de test, pour vérifier la configuration du webhook Slack.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final SlackNotifier slackNotifier;

    public NotificationController(SlackNotifier slackNotifier) {
        this.slackNotifier = slackNotifier;
    }

    public record NotificationTestResult(boolean sent, String message) {
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public NotificationTestResult sendTest(Authentication authentication) {
        if (!slackNotifier.isEnabled()) {
            return new NotificationTestResult(false,
                    "Notifications Slack désactivées : renseignez SMARTDEPLOY_SLACK_WEBHOOK_URL dans le fichier .env, puis redémarrez le backend.");
        }
        AuditLog sample = AuditLog.builder()
                .action(AuditAction.RESOURCE_UPDATED)
                .actor(authentication == null ? "Système" : authentication.getName())
                .summary("Message de test : les notifications SmartDeploy sont bien configurées.")
                .occurredAt(LocalDateTime.now())
                .build();
        boolean sent = slackNotifier.notify(sample);
        return new NotificationTestResult(sent, sent
                ? "Message de test envoyé : vérifiez le canal Slack."
                : "Échec de l'envoi : vérifiez l'URL du webhook et l'accès Internet du serveur (détails dans les logs).");
    }
}
