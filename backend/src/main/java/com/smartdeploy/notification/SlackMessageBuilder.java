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
            case DEPLOYMENT_SUCCEEDED -> "Déploiement réussi";
            case DEPLOYMENT_FAILED -> "Déploiement échoué";
            case DEPLOYMENT_CANCELLED -> "Déploiement annulé";
            case DEPLOYMENT_APPROVAL_REQUESTED -> "Déploiement en production : approbation demandée";
            case DEPLOYMENT_APPROVED -> "Déploiement en production approuvé";
            case DEPLOYMENT_REJECTED -> "Déploiement en production refusé";
            case DEPLOYMENT_APPROVAL_EXPIRED -> "Demande d'approbation expirée";
            case DEPLOYMENT_EMERGENCY_OVERRIDE -> "Exception d'urgence : déploiement sans validation préalable";
            case BACKUP_PROTECTION_DEGRADED -> "Protection des données dégradée";
            case BACKUP_PROTECTION_RESTORED -> "Protection des données rétablie";
            case BACKUP_STORAGE_UNAVAILABLE -> "Stockage des sauvegardes indisponible";
            case BACKUP_STORAGE_AVAILABLE -> "Stockage des sauvegardes de nouveau disponible";
            case PLATFORM_BACKUP_SUCCEEDED -> "Base de SmartDeploy sauvegardée";
            case PLATFORM_BACKUP_FAILED -> "Échec de la sauvegarde de la base de SmartDeploy";
            case RESTORE_VERIFIED -> "Application de nouveau en bonne santé après restauration";
            case RESTORE_UNHEALTHY -> "Application non rétablie après restauration";
            case STORAGE_SPACE_LOW -> "Espace disque du RAID bientôt saturé";
            case STORAGE_SPACE_CRITICAL -> "Espace disque du RAID presque plein";
            case STORAGE_SPACE_OK -> "Espace disque du RAID de nouveau suffisant";
            case BACKUP_DELETED -> "Sauvegarde supprimée";
            case DEPLOYMENT_ROLLBACK_REQUESTED -> "Rollback demandé";
            case DEPLOYMENT_EXPIRED -> "Déploiement bloqué";
            case SUSPICIOUS_ACTIVITY -> "Activité suspecte";
            case USER_ROLE_CHANGED -> "Rôle d'un utilisateur modifié";
            case USER_STATUS_CHANGED -> "Statut d'un compte modifié";
            case ALERT_OPENED -> "Alerte de supervision";
            case ALERT_RESOLVED -> "Alerte résolue";
            case GUARD_DEGRADED -> "Dégradation détectée par le gardien";
            case GUARD_PASSED -> "Déploiement validé par le gardien";
            case GUARD_INCONCLUSIVE -> "Surveillance non concluante";
            case GUARD_AUTO_ROLLBACK -> "Retour arrière automatique";
            case INCIDENT_ANALYZED -> "Analyse de l'incident";
            case BACKUP_REQUESTED -> "Sauvegarde demandée";
            case BACKUP_COMPLETED -> "Sauvegarde terminée";
            case BACKUP_FAILED -> "Sauvegarde en échec";
            case RESTORE_STARTED -> "Restauration lancée";
            case RESTORE_COMPLETED -> "Restauration terminée";
            case RESTORE_FAILED -> "Restauration en échec";
            case RAID_DEGRADED -> "RAID dégradé : un disque est en panne";
            case RAID_RECOVERING -> "RAID en reconstruction";
            case RAID_RESTORED -> "RAID de nouveau sain";
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
