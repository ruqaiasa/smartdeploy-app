package com.smartdeploy.environment;

import java.text.Normalizer;
import java.util.Locale;

public final class EnvironmentNamespaceGenerator {

    private EnvironmentNamespaceGenerator() {
    }

    public static String generate(
            String applicationName,
            EnvironmentType type
    ) {
        if (applicationName == null || applicationName.isBlank()) {
            throw new IllegalArgumentException(
                    "Le nom de l'application est obligatoire"
            );
        }

        if (type == null) {
            throw new IllegalArgumentException(
                    "Le type d'environnement est obligatoire"
            );
        }

        String environmentCode = switch (type.name()) {
            case "DEVELOPMENT" -> "dev";
            case "TEST" -> "test";
            case "STAGING" -> "staging";
            case "PRODUCTION" -> "prod";
            default -> type.name().toLowerCase(Locale.ROOT);
        };

        String normalizedName = Normalizer.normalize(
                        applicationName,
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException(
                    "Impossible de générer un namespace valide"
            );
        }

        String prefix = "sd-" + environmentCode + "-";

        // Respecter la limite de 63 caractères.
        int maxNameLength = 63 - prefix.length();

        if (normalizedName.length() > maxNameLength) {
            normalizedName = normalizedName.substring(
                    0,
                    maxNameLength
            ).replaceAll("-+$", "");
        }

        return prefix + normalizedName;
    }
}