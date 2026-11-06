package com.smartdeploy.backup;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Règles de nommage et de lecture des objets Velero (sans dépendance, testables). */
public final class BackupNames {

    static final String LABEL_REASON = "smartdeploy.io/reason";
    static final String LABEL_ENVIRONMENT = "smartdeploy.io/environment-id";
    static final String LABEL_DEPLOYMENT = "smartdeploy.io/deployment-id";
    static final String LABEL_SCHEDULE = "velero.io/schedule-name";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private BackupNames() {
    }

    /** Nom conforme à Kubernetes : minuscules, chiffres et tirets, 63 caractères au plus. */
    static String name(String prefix, String subject, LocalDateTime at) {
        String clean = (prefix + "-" + subject).toLowerCase().replaceAll("[^a-z0-9-]+", "-").replaceAll("-+", "-");
        String suffix = "-" + at.format(STAMP);
        if (clean.length() + suffix.length() > 63) {
            clean = clean.substring(0, 63 - suffix.length());
        }
        return clean.replaceAll("-+$", "") + suffix;
    }

    /** Vrai si la sauvegarde contient l'espace de noms (explicitement, ou par une sauvegarde globale). */
    @SuppressWarnings("unchecked")
    static boolean covers(GenericKubernetesResource backup, String namespace) {
        Object spec = backup.getAdditionalProperties().get("spec");
        if (!(spec instanceof Map<?, ?> map)) {
            return false;
        }
        List<String> included = (List<String>) map.get("includedNamespaces");
        List<String> excluded = (List<String>) map.get("excludedNamespaces");
        if (included != null && !included.isEmpty() && !included.contains("*")) {
            return included.contains(namespace);
        }
        return excluded == null || !excluded.contains(namespace);
    }

    static String origin(GenericKubernetesResource backup) {
        Map<String, String> labels = backup.getMetadata().getLabels() == null ? Map.of() : backup.getMetadata().getLabels();
        if (labels.containsKey(LABEL_SCHEDULE)) {
            return "SCHEDULED";
        }
        return "pre-deployment".equals(labels.get(LABEL_REASON)) ? "PRE_DEPLOYMENT" : "MANUAL";
    }

    static BackupInfo toInfo(GenericKubernetesResource backup) {
        Map<?, ?> status = backup.getAdditionalProperties().get("status") instanceof Map<?, ?> m ? m : Map.of();
        Map<?, ?> progress = status.get("progress") instanceof Map<?, ?> p ? p : Map.of();
        Map<String, String> labels = backup.getMetadata().getLabels() == null ? Map.of() : backup.getMetadata().getLabels();
        return new BackupInfo(
                backup.getMetadata().getName(),
                origin(backup),
                labels.containsKey(LABEL_DEPLOYMENT) ? Long.valueOf(labels.get(LABEL_DEPLOYMENT)) : null,
                VeleroClient.phase(backup),
                text(status.get("startTimestamp")),
                text(status.get("completionTimestamp")),
                text(status.get("expiration")),
                number(progress.get("itemsBackedUp")),
                number(status.get("errors")),
                number(status.get("warnings")));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer number(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }
}
