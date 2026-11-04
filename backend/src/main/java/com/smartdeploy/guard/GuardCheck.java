package com.smartdeploy.guard;

/**
 * Résultat d'un contrôle : ce qui a été mesuré, la référence et l'explication.
 * Chaque décision du gardien repose sur ces preuves, affichées telles quelles.
 */
public record GuardCheck(
        String key,
        String label,
        GuardLevel level,
        String observed,
        String reference,
        String explanation
) {
}
