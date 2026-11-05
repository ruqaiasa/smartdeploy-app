package com.smartdeploy.incident;

/** Origine de l'analyse. */
public enum IncidentTrigger {
    /** Échec du pipeline de déploiement. */
    PIPELINE,
    /** Dégradation détectée par le gardien après la mise en service. */
    GUARD,
    /** Analyse relancée par un utilisateur. */
    MANUAL
}
