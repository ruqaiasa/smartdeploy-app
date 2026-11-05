package com.smartdeploy.notification;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Décide quelles entrées du journal sont envoyées sur Slack, et les envoie.
 * Désactivé tant que SMARTDEPLOY_SLACK_WEBHOOK_URL n'est pas renseignée.
 * L'URL du webhook est un secret : elle n'est jamais journalisée.
 */
@Component
public class SlackNotifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    static final String DEFAULT_ACTIONS = "DEPLOYMENT_SUCCEEDED,DEPLOYMENT_FAILED,DEPLOYMENT_ROLLBACK_REQUESTED,"
            + "DEPLOYMENT_EXPIRED,SUSPICIOUS_ACTIVITY,USER_ROLE_CHANGED,ALERT_OPENED,ALERT_RESOLVED,GUARD_DEGRADED,GUARD_AUTO_ROLLBACK,INCIDENT_ANALYZED";

    private final SlackClient slackClient;
    private final String webhookUrl;
    private final String appUrl;
    private final Set<AuditAction> notifiedActions;

    public SlackNotifier(
            SlackClient slackClient,
            @Value("${smartdeploy.notifications.slack.webhook-url:}") String webhookUrl,
            @Value("${smartdeploy.notifications.slack.actions:" + DEFAULT_ACTIONS + "}") String actions,
            @Value("${smartdeploy.app-url:http://localhost:5173}") String appUrl
    ) {
        this.slackClient = slackClient;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.appUrl = appUrl;
        this.notifiedActions = parseActions(actions);
        if (this.webhookUrl.isEmpty()) {
            log.info("Notifications Slack désactivées : SMARTDEPLOY_SLACK_WEBHOOK_URL non renseignée");
        }
    }

    public boolean isEnabled() {
        return !webhookUrl.isEmpty();
    }

    public boolean shouldNotify(AuditAction action) {
        return isEnabled() && notifiedActions.contains(action);
    }

    /** Envoie la notification ; ne lève jamais d'exception (Slack ne doit pas bloquer SmartDeploy). */
    public boolean notify(AuditLog entry) {
        if (!isEnabled()) {
            return false;
        }
        try {
            slackClient.send(webhookUrl, SlackMessageBuilder.build(entry, appUrl));
            return true;
        } catch (RuntimeException exception) {
            log.warn("Notification Slack non envoyée pour {} : {}", entry.getAction(), exception.getMessage());
            return false;
        }
    }

    static Set<AuditAction> parseActions(String actions) {
        Set<AuditAction> result = EnumSet.noneOf(AuditAction.class);
        if (actions == null) {
            return result;
        }
        Arrays.stream(actions.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(value -> {
                    try {
                        result.add(AuditAction.valueOf(value));
                    } catch (IllegalArgumentException ignored) {
                        log.warn("Action de notification inconnue ignorée : {}", value);
                    }
                });
        return result;
    }
}
