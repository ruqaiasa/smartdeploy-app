package com.smartdeploy.incident;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Mémoire des incidents : la même panne est reconnue, des pannes différentes ne sont pas confondues. */
class IncidentSimilarityTest {

    private static List<String> compilation(String ligne, String pod) {
        return IncidentSimilarity.tokens("COMPILATION_ERROR", "Erreur de compilation", "Build Maven",
                "Fichier MessageService.java Ligne " + ligne + " Erreur ';' expected",
                "[ERROR] /var/lib/jenkins/workspace/smartdeploy/backend/src/main/java/com/spring/render/services/MessageService.java:[" + ligne + ",31] ';' expected " + pod);
    }

    private static List<String> plantage(String pod) {
        return IncidentSimilarity.tokens("CRASH_LOOP", "L'application plante en boucle", null,
                "Code de sortie 1 Arrêt en cours d'exécution",
                "Panne simulée : arrêt du service après le démarrage " + pod);
    }

    @Test
    void numbersDatesAndPodSuffixesAreIgnored() {
        List<String> tokens = compilation("38", "fullstack-demo-backend-85cf7d9b4-xk2lp");
        assertTrue(tokens.contains("messageservice.java"));
        assertTrue(tokens.stream().noneMatch(t -> t.matches(".*\\d.*") && !t.startsWith("cause:")), tokens.toString());
    }

    @Test
    void theSamePanicIsRecognisedDespiteDifferentNumbers() {
        Map<Long, List<String>> corpus = new LinkedHashMap<>();
        corpus.put(131L, compilation("38", "pod-a1b2c3d4"));
        corpus.put(129L, plantage("fullstack-demo-backend-85cf7d9b4-xk2lp"));

        List<IncidentSimilarity.Match> matches = IncidentSimilarity.rank(compilation("42", "pod-ffee9911"), corpus, 3, 0.30);

        assertEquals(131L, matches.get(0).id(), "la compilation ratée est retrouvée en premier");
        assertTrue(matches.get(0).score() > 0.9, "score " + matches.get(0).score());
        assertTrue(matches.get(0).sharedTerms().contains("même cause"));
        assertTrue(matches.stream().noneMatch(m -> m.id() == 129L), "le plantage, sans rapport, est écarté");
    }

    @Test
    void anEmptyHistoryGivesNoMatch() {
        assertTrue(IncidentSimilarity.rank(plantage("x"), Map.of(), 3, 0.30).isEmpty());
    }
}
