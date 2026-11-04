package com.smartdeploy.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Chaîne d'empreintes du journal : hash = SHA-256(previousHash + contenu).
 * Modifier une entrée change son empreinte ; supprimer une entrée rompt
 * le lien previousHash de la suivante.
 */
public final class AuditChain {

    /** Empreinte de départ de la chaîne. */
    public static final String GENESIS = "0".repeat(64);

    private static final char SEPARATOR = '\u001f';

    private AuditChain() {
    }

    public static String hash(AuditLog entry) {
        String canonical = String.join(String.valueOf(SEPARATOR),
                value(entry.getPreviousHash()),
                value(entry.getAction()),
                value(entry.getActor()),
                value(entry.getTargetType()),
                value(entry.getTargetId()),
                value(entry.getApplicationName()),
                value(entry.getEnvironmentName()),
                value(entry.getSummary()),
                value(entry.getDetails()),
                value(entry.getIpAddress()),
                value(entry.getUserAgent()),
                value(entry.getOccurredAt())
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
