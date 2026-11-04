package com.smartdeploy.notification;

import com.smartdeploy.audit.AuditRecordedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Notifie l'équipe à partir des entrées du journal d'audit : tout ce qui est
 * notifié est donc aussi tracé. Envoi asynchrone, en dehors du traitement
 * de la requête : une lenteur de Slack n'affecte jamais SmartDeploy.
 */
@Component
public class NotificationListener {

    private final SlackNotifier slackNotifier;

    public NotificationListener(SlackNotifier slackNotifier) {
        this.slackNotifier = slackNotifier;
    }

    @Async
    @EventListener
    public void onAuditRecorded(AuditRecordedEvent event) {
        if (slackNotifier.shouldNotify(event.entry().getAction())) {
            slackNotifier.notify(event.entry());
        }
    }
}
