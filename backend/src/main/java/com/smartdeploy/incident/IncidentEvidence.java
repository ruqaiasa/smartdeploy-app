package com.smartdeploy.incident;

import java.util.List;

/**
 * Preuves rassemblées pour un incident. Chaque liste peut être vide si la
 * source est indisponible ; la raison figure alors dans {@code notes}.
 *
 * @param failedStage    étape du pipeline en échec (null pour une dégradation)
 * @param errorMessage   message d'échec enregistré sur le déploiement
 * @param jenkinsLog     fin du log de la console Jenkins
 * @param podStates      état des conteneurs (« pod/conteneur : raison… »)
 * @param events         événements Kubernetes d'avertissement
 * @param containerLogs  fin des logs des conteneurs en difficulté
 * @param guardChecks    contrôles du gardien en défaut
 * @param notes          sources indisponibles et pourquoi
 */
public record IncidentEvidence(
        String failedStage,
        String errorMessage,
        List<String> jenkinsLog,
        List<String> podStates,
        List<String> events,
        List<String> containerLogs,
        List<String> guardChecks,
        List<String> notes
) {
    public IncidentEvidence {
        jenkinsLog = jenkinsLog == null ? List.of() : List.copyOf(jenkinsLog);
        podStates = podStates == null ? List.of() : List.copyOf(podStates);
        events = events == null ? List.of() : List.copyOf(events);
        containerLogs = containerLogs == null ? List.of() : List.copyOf(containerLogs);
        guardChecks = guardChecks == null ? List.of() : List.copyOf(guardChecks);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** Sources de preuves, telles qu'affichées dans le diagnostic. */
    public enum Source {
        STAGE("Étape en échec"),
        ERROR("Message d'échec"),
        JENKINS("Log Jenkins"),
        POD("État des pods"),
        EVENT("Événement Kubernetes"),
        CONTAINER("Log du conteneur"),
        GUARD("Gardien");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    List<String> lines(Source source) {
        return switch (source) {
            case STAGE -> failedStage == null ? List.of() : List.of(failedStage);
            case ERROR -> errorMessage == null ? List.of() : List.of(errorMessage);
            case JENKINS -> jenkinsLog;
            case POD -> podStates;
            case EVENT -> events;
            case CONTAINER -> containerLogs;
            case GUARD -> guardChecks;
        };
    }
}
