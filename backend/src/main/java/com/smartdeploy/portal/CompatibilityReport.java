package com.smartdeploy.portal;

import java.util.List;

/**
 * Rapport de compatibilité d'un dépôt avec SmartDeploy.
 *
 * @param verdict READY, ALMOST_READY ou NOT_READY
 * @param recommendedArchitecture BACKEND, FULLSTACK_EXTERNAL ou FULLSTACK_MANAGED (null si inconnue)
 */
public record CompatibilityReport(String repository, String branch, String verdict, String verdictLabel,
                                  String recommendedArchitecture, String architectureLabel,
                                  String backendDirectory, String frontendDirectory, List<Check> checks) {

    public enum Status { OK, INFO, WARNING, ERROR }

    /** Un point vérifié : son résultat, ce qui a été trouvé, et comment corriger s'il le faut. */
    public record Check(String id, String label, Status status, String detail, String fix) {
    }
}
