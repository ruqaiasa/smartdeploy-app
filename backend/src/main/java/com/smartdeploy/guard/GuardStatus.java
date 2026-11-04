package com.smartdeploy.guard;

/** État de la surveillance d'un déploiement par le gardien. */
public enum GuardStatus {
    /** Surveillance en cours. */
    WATCHING,
    /** Fenêtre écoulée sans anomalie. */
    HEALTHY,
    /** Fenêtre écoulée, avec des avertissements non bloquants. */
    HEALTHY_WITH_WARNINGS,
    /** Anomalie critique confirmée : un retour arrière est proposé. */
    DEGRADED,
    /** Aucune métrique exploitable pendant la fenêtre. */
    INCONCLUSIVE,
    /** Un autre déploiement a pris le relais sur l'environnement. */
    SUPERSEDED;

    public boolean isFinal() {
        return this != WATCHING;
    }
}
