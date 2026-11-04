package com.smartdeploy.notification;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SlackMessageBuilderTest {

    private static AuditLog entry(AuditAction action, String summary) {
        return AuditLog.builder()
                .action(action).actor("Jenkins").summary(summary)
                .applicationName("fullstack-demo").environmentName("fullstack-demo-dev")
                .occurredAt(LocalDateTime.of(2026, 11, 4, 10, 30))
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildReadableMessageWithLink() {
        Map<String, Object> payload = SlackMessageBuilder.build(
                entry(AuditAction.DEPLOYMENT_FAILED, "Échec à l'étape « Trivy Security Scan »"),
                "http://localhost:5173/");

        assertTrue(((String) payload.get("text")).startsWith("❌ Déploiement échoué"));
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) payload.get("blocks");
        String all = blocks.toString();
        assertTrue(all.contains("*fullstack-demo*"));
        assertTrue(all.contains("par Jenkins"));
        assertTrue(all.contains("<http://localhost:5173/deployments|Ouvrir SmartDeploy>"));
    }

    @Test
    void shouldEscapeUserTextToPreventMentionsAndLinks() {
        assertEquals("&lt;!channel&gt; a &amp; b", SlackMessageBuilder.escape("<!channel> a & b"));
    }

    @Test
    void shouldLinkSecurityEventsToTheJournal() {
        String blocks = SlackMessageBuilder.build(
                entry(AuditAction.SUSPICIOUS_ACTIVITY, "Tentatives répétées"), "http://app").get("blocks").toString();
        assertTrue(blocks.contains("http://app/audit"));
        assertEquals("🚨 Activité suspecte", SlackMessageBuilder.title(AuditAction.SUSPICIOUS_ACTIVITY));
    }
}
