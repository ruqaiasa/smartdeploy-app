package com.smartdeploy.notification;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditLog;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construit le message Slack (format « Block Kit ») d'une entrée du journal.
 */
public final class SlackMessageBuilder {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private SlackMessageBuilder() {
    }

    public static String title(AuditAction action) {
        return switch (action) {
            case DEPLOYMENT_SUCCEEDED -> "✅ Déploiement réussi";
            case DEPLOYMENT_FAILED -> "❌ Déploiement échoué";
            case DEPLOYMENT_CANCELLED -> "⏹️ Déploiement annulé";
            case DEPLOYMENT_ROLLBACK_REQUESTED -> "↩️ Rollback demandé";
            case DEPLOYMENT_EXPIRED -> "⏱️ Déploiement bloqué";
            case SUSPICIOUS_ACTIVITY -> "🚨 Activité suspecte";
            case USER_ROLE_CHANGED -> "🔐 Rôle d'un utilisateur modifié";
            case USER_STATUS_CHANGED -> "🔐 Statut d'un compte modifié";
            case ALERT_OPENED -> "⚠️ Alerte de supervision";
            case ALERT_RESOLVED -> "✅ Alerte résolue";
            default -> "ℹ️ SmartDeploy";
        };
    }

    public static Map<String, Object> build(AuditLog entry, String appUrl) {
        String title = title(entry.getAction());
        String summary = escape(entry.getSummary());

        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(section("*" + title + "*\n" + summary));

        if (entry.getDetails() != null && !entry.getDetails().isBlank()) {
            blocks.add(context(escape(entry.getDetails())));
        }

        StringBuilder meta = new StringBuilder();
        if (entry.getApplicationName() != null) {
            meta.append("*").append(escape(entry.getApplicationName())).append("*");
            if (entry.getEnvironmentName() != null) {
                meta.append(" · ").append(escape(entry.getEnvironmentName()));
            }
            meta.append("  |  ");
        }
        meta.append("par ").append(escape(entry.getActor()));
        if (entry.getOccurredAt() != null) {
            meta.append("  |  ").append(entry.getOccurredAt().format(TIME));
        }
        if (appUrl != null && !appUrl.isBlank()) {
            meta.append("  |  <").append(appUrl.replaceAll("/+$", "")).append(link(entry.getAction()))
                    .append("|Ouvrir SmartDeploy>");
        }
        blocks.add(context(meta.toString()));

        Map<String, Object> payload = new LinkedHashMap<>();
        // Texte de repli : notifications mobiles et clients sans Block Kit.
        payload.put("text", title + " : " + entry.getSummary());
        payload.put("blocks", blocks);
        return payload;
    }

    private static String link(AuditAction action) {
        return switch (action) {
            case SUSPICIOUS_ACTIVITY, USER_ROLE_CHANGED, USER_STATUS_CHANGED -> "/audit";
            case ALERT_OPENED, ALERT_RESOLVED -> "/alerts";
            default -> "/deployments";
        };
    }

    private static Map<String, Object> section(String markdown) {
        return Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", markdown));
    }

    private static Map<String, Object> context(String markdown) {
        return Map.of("type", "context", "elements", List.of(Map.of("type", "mrkdwn", "text", markdown)));
    }

    /** Caractères réservés de Slack : un texte utilisateur ne doit pas créer de lien ni de mention. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
