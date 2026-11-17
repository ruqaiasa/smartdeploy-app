package com.smartdeploy.backup;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * État de protection d'un environnement (décision pure, testable) : la sauvegarde
 * planifiée a lieu chaque nuit, donc une dernière sauvegarde réussie de moins de
 * 26 h est normale ; au-delà de 50 h, ou si la dernière tentative a échoué, c'est un risque.
 */
public final class BackupProtection {

    static final Duration WARNING_AFTER = Duration.ofHours(26);
    static final Duration CRITICAL_AFTER = Duration.ofHours(50);

    public enum Level { OK, WARNING, CRITICAL }

    public record EnvironmentProtection(
            Long environmentId,
            String applicationName,
            String environmentName,
            String namespace,
            Level level,
            String lastBackupName,
            String lastBackupAt,
            String message
    ) {
    }

    private BackupProtection() {
    }

    public static EnvironmentProtection evaluate(BackupEnvironment environment, List<BackupInfo> backups, Instant now) {
        BackupInfo lastCompleted = backups.stream()
                .filter(b -> "Completed".equals(b.phase()) && b.completedAt() != null)
                .max(Comparator.comparing(BackupInfo::completedAt))
                .orElse(null);
        BackupInfo lastAttempt = backups.stream()
                .filter(b -> b.startedAt() != null)
                .max(Comparator.comparing(BackupInfo::startedAt))
                .orElse(null);
        boolean lastAttemptFailed = lastAttempt != null
                && List.of("Failed", "PartiallyFailed", "FailedValidation").contains(lastAttempt.phase());

        Level level;
        String message;
        if (lastCompleted == null) {
            level = lastAttemptFailed ? Level.CRITICAL : Level.WARNING;
            message = lastAttemptFailed ? "La seule sauvegarde tentée a échoué" : "Aucune sauvegarde réussie pour l'instant : la première aura lieu cette nuit";
        } else {
            Duration age = Duration.between(Instant.parse(lastCompleted.completedAt()), now);
            if (lastAttemptFailed && !Objects.equals(lastAttempt.name(), lastCompleted.name())) {
                level = Level.CRITICAL;
                message = "La dernière sauvegarde a échoué (" + lastAttempt.name() + ")";
            } else if (age.compareTo(CRITICAL_AFTER) > 0) {
                level = Level.CRITICAL;
                message = "Dernière sauvegarde réussie il y a " + age.toHours() + " h";
            } else if (age.compareTo(WARNING_AFTER) > 0) {
                level = Level.WARNING;
                message = "Dernière sauvegarde réussie il y a " + age.toHours() + " h";
            } else {
                level = Level.OK;
                message = "Protégé : dernière sauvegarde il y a " + humanAge(age);
            }
        }
        return new EnvironmentProtection(environment.environmentId(), environment.applicationName(),
                environment.environmentName(), environment.namespace(), level,
                lastCompleted == null ? null : lastCompleted.name(),
                lastCompleted == null ? null : lastCompleted.completedAt(), message);
    }

    static String humanAge(Duration age) {
        long minutes = Math.max(0, age.toMinutes());
        return minutes < 60 ? minutes + " min" : (minutes / 60) + " h";
    }

    /** Date Velero (UTC, ISO-8601) → heure locale du serveur, comme les dates des déploiements. */
    static LocalDateTime localTime(String veleroTimestamp) {
        return veleroTimestamp == null ? null
                : LocalDateTime.ofInstant(Instant.parse(veleroTimestamp), ZoneId.systemDefault());
    }
}
