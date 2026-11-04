package com.smartdeploy.notification;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackNotifierTest {

    @Mock
    private SlackClient slackClient;

    private static final AuditLog ENTRY = AuditLog.builder()
            .action(AuditAction.DEPLOYMENT_SUCCEEDED).actor("Jenkins").summary("Déploiement #111 réussi")
            .occurredAt(LocalDateTime.now()).build();

    @Test
    void shouldStayDisabledWithoutWebhook() {
        SlackNotifier notifier = new SlackNotifier(slackClient, " ", SlackNotifier.DEFAULT_ACTIONS, "http://app");

        assertFalse(notifier.isEnabled());
        assertFalse(notifier.shouldNotify(AuditAction.DEPLOYMENT_SUCCEEDED));
        assertFalse(notifier.notify(ENTRY));
        verifyNoInteractions(slackClient);
    }

    @Test
    void shouldNotifyOnlySelectedActions() {
        SlackNotifier notifier = new SlackNotifier(slackClient, "https://hooks.slack.com/services/T/B/X",
                "DEPLOYMENT_FAILED, SUSPICIOUS_ACTIVITY, ACTION_INCONNUE", "http://app");

        assertTrue(notifier.shouldNotify(AuditAction.DEPLOYMENT_FAILED));
        assertTrue(notifier.shouldNotify(AuditAction.SUSPICIOUS_ACTIVITY));
        assertFalse(notifier.shouldNotify(AuditAction.LOGIN_SUCCESS));
    }

    @Test
    void shouldSendToWebhookAndNeverPropagateSlackErrors() {
        SlackNotifier notifier = new SlackNotifier(slackClient, "https://hooks.slack.com/services/T/B/X",
                SlackNotifier.DEFAULT_ACTIONS, "http://app");

        assertTrue(notifier.notify(ENTRY));
        verify(slackClient).send(eq("https://hooks.slack.com/services/T/B/X"), any());

        doThrow(new RuntimeException("Slack indisponible")).when(slackClient).send(any(), any());
        assertFalse(assertDoesNotThrow(() -> notifier.notify(ENTRY)));
    }
}
