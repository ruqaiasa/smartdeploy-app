package com.smartdeploy.deployment;

/**
 * Résultat du traitement d'un callback Jenkins.
 */
public enum PipelineCallbackOutcome {

    /** Le statut et/ou les métadonnées ont été appliqués. */
    APPLIED,

    /** Callback répété : le déploiement possède déjà ce statut. */
    DUPLICATE,

    /** Callback tardif ou hors ordre sur un déploiement déjà terminé. */
    IGNORED
}
