package com.smartdeploy.audit;

import java.time.LocalDateTime;

/**
 * Résultat de la vérification de la chaîne d'empreintes du journal.
 *
 * @param unchainedEntries entrées antérieures à la chaîne (sans empreinte)
 * @param firstInvalidId   première entrée en défaut, ou null si intègre
 */
public record AuditIntegrityReport(
        boolean valid,
        long checkedEntries,
        long unchainedEntries,
        Long firstInvalidId,
        String message,
        LocalDateTime verifiedAt
) {
}
