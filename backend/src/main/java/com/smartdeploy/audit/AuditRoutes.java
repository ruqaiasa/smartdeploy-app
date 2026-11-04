package com.smartdeploy.audit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduit une requête HTTP qui modifie des données en entrée lisible du
 * journal. Les routes déjà tracées par des événements métier (déploiements,
 * connexion, rôle et statut d'un utilisateur) ne sont tracées ici qu'en cas
 * d'échec, pour ne pas créer de doublons.
 */
public final class AuditRoutes {

    /** Entrée à journaliser pour une requête réussie, ou SKIP. */
    public record RouteAudit(AuditAction action, String targetType, Long targetId, String summary) {
        static final RouteAudit SKIP = new RouteAudit(null, null, null, null);

        boolean skipped() {
            return action == null;
        }
    }

    private static final Pattern RESOURCE = Pattern.compile(
            "^/api/(applications|environments|versions|users)(?:/(\\d+))?(?:/(activate|deactivate|restore|status|metadata|role))?$");

    private AuditRoutes() {
    }

    public static boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    /** Routes jamais tracées : lectures en POST, renouvellement de jeton, connexion (déjà tracée). */
    public static boolean isIgnored(String path) {
        return !path.startsWith("/api/")
                || path.startsWith("/api/kubernetes/manifests/")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/login");
    }

    /** Routes tracées par des événements métier quand elles réussissent. */
    public static boolean isCoveredByDomainEvents(String method, String path) {
        return path.startsWith("/api/deployments")
                || path.equals("/api/webhooks/jenkins")
                || path.matches("^/api/users/\\d+/(role|status)$");
    }

    public static RouteAudit describe(String method, String path) {
        if (path.equals("/api/auth/logout")) {
            return new RouteAudit(AuditAction.LOGOUT, "USER", null, "Déconnexion");
        }
        if (path.equals("/api/kubernetes/secrets") && "POST".equals(method)) {
            return new RouteAudit(AuditAction.RESOURCE_CREATED, "SECRET", null,
                    "Secret Kubernetes créé (sa valeur n'est jamais journalisée)");
        }
        if (path.equals("/api/notifications/test")) {
            return new RouteAudit(AuditAction.RESOURCE_UPDATED, null, null, "Notification Slack de test envoyée");
        }
        if (path.equals("/api/users/register")) {
            return new RouteAudit(AuditAction.RESOURCE_CREATED, "USER", null, "Inscription d'un nouvel utilisateur");
        }
        if (path.equals("/api/users/me")) {
            return new RouteAudit(AuditAction.RESOURCE_UPDATED, "USER", null, "Profil personnel modifié");
        }
        if (path.equals("/api/users/change-password")) {
            return new RouteAudit(AuditAction.RESOURCE_UPDATED, "USER", null, "Mot de passe modifié");
        }

        Matcher matcher = RESOURCE.matcher(path);
        if (matcher.matches()) {
            String collection = matcher.group(1);
            Long id = matcher.group(2) == null ? null : Long.valueOf(matcher.group(2));
            String operation = matcher.group(3);
            String noun = noun(collection);
            String agreement = feminine(collection) ? "e" : "";
            String target = noun + (id == null ? "" : " #" + id);
            String type = collection.substring(0, collection.length() - 1).toUpperCase();

            if (operation == null) {
                return switch (method) {
                    case "POST" -> new RouteAudit(AuditAction.RESOURCE_CREATED, type, null, noun + " créé" + agreement);
                    case "DELETE" -> new RouteAudit(AuditAction.RESOURCE_DELETED, type, id, target + " supprimé" + agreement);
                    default -> new RouteAudit(AuditAction.RESOURCE_UPDATED, type, id, target + " modifié" + agreement);
                };
            }
            return switch (operation) {
                case "activate" -> new RouteAudit(AuditAction.RESOURCE_UPDATED, type, id, target + " activé" + agreement);
                case "deactivate" -> new RouteAudit(AuditAction.RESOURCE_UPDATED, type, id, target + " désactivé" + agreement);
                case "restore" -> new RouteAudit(AuditAction.RESOURCE_UPDATED, type, id, target + " restauré" + agreement);
                case "status" -> new RouteAudit(AuditAction.RESOURCE_UPDATED, type, id, "Statut de " + withArticle(collection, id) + " modifié");
                case "metadata" -> new RouteAudit(AuditAction.RESOURCE_UPDATED, type, id, "Métadonnées de " + withArticle(collection, id) + " modifiées");
                case "role" -> new RouteAudit(AuditAction.RESOURCE_UPDATED, type, id, "Rôle de " + withArticle(collection, id) + " modifié");
                default -> generic(method, path);
            };
        }

        // Route future non répertoriée : tracée quand même, pour une couverture complète.
        return generic(method, path);
    }

    private static RouteAudit generic(String method, String path) {
        return new RouteAudit(AuditAction.RESOURCE_UPDATED, null, null, "Action " + method + " " + path);
    }

    private static String noun(String collection) {
        return switch (collection) {
            case "applications" -> "Application";
            case "environments" -> "Environnement";
            case "versions" -> "Version";
            case "users" -> "Utilisateur";
            default -> collection;
        };
    }

    /** « la version #9 », « l'utilisateur #2 », « l'environnement #4 ». */
    private static String withArticle(String collection, Long id) {
        String article = switch (collection) {
            case "versions" -> "la version";
            case "applications" -> "l'application";
            case "environments" -> "l'environnement";
            default -> "l'utilisateur";
        };
        return article + (id == null ? "" : " #" + id);
    }

    private static boolean feminine(String collection) {
        return collection.equals("applications") || collection.equals("versions");
    }
}
