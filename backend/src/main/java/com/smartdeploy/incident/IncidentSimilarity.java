package com.smartdeploy.incident;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Proximité entre deux incidents, par similarité cosinus sur des vecteurs TF-IDF.
 *
 * Chaque analyse devient un « document » de mots-clés : sa cause (comptée deux fois, c'est le
 * signal le plus fort), l'étape en échec, les faits établis et la ligne décisive. Ce qui change
 * d'un incident à l'autre sans rien dire de la panne est retiré : nombres, dates, empreintes,
 * suffixes aléatoires des pods. Le poids TF-IDF favorise les mots rares et significatifs (un nom
 * de fichier, un code d'erreur) au détriment des mots que l'on retrouve partout.
 * Calcul pur, sans base de données ni service externe : explicable et testable.
 */
public final class IncidentSimilarity {

    static final double IDF_FLOOR = 0.2;
    private static final Pattern HEX = Pattern.compile("\\b[0-9a-f]{6,}\\b");
    private static final Pattern NUMBERS = Pattern.compile("\\d+");
    private static final Pattern SPLIT = Pattern.compile("[^a-z0-9._-]+");
    private static final Set<String> STOP = Set.of(
            "les", "des", "une", "est", "pour", "dans", "par", "sur", "avec", "pas", "qui", "que", "aux", "son", "ses",
            "the", "and", "for", "with", "not", "was", "has", "from", "this", "that", "are", "error", "erreur",
            "log", "jenkins", "info", "warn", "warning", "at", "fichier", "ligne", "valeur",
            // Mots de structure des preuves, présents dans presque toutes : ils ne distinguent aucun incident.
            "etat", "pods", "pod", "pret", "running", "container", "conteneur", "evenement", "kubernetes", "gardien",
            "depuis", "deploiement", "reference", "apres", "min", "dernier", "arret", "redemarrage", "redemarrages",
            "in", "of", "to", "sd-test", "sd-dev", "sd-prod", "moins", "plus", "sortie");

    /** Un incident proche, avec les mots qu'il partage le plus fortement avec l'incident courant. */
    public record Match(Long id, double score, List<String> sharedTerms) {
    }

    private IncidentSimilarity() {
    }

    /** Mots-clés normalisés d'une analyse. */
    public static List<String> tokens(String code, String title, String stage, String facts, String decisiveLine) {
        List<String> tokens = new ArrayList<>();
        if (code != null && !code.isBlank()) {
            tokens.add("cause:" + code.toLowerCase(Locale.ROOT));
            tokens.add("cause:" + code.toLowerCase(Locale.ROOT));
        }
        if (stage != null && !stage.isBlank()) {
            tokens.add("etape:" + normalize(stage).replaceAll("\\s+", "_"));
        }
        for (String text : new String[]{title, facts, decisiveLine}) {
            words(text, tokens);
        }
        return tokens;
    }

    static void words(String text, List<String> into) {
        if (text == null) {
            return;
        }
        String clean = HEX.matcher(normalize(text)).replaceAll(" ");
        clean = NUMBERS.matcher(clean).replaceAll(" ");
        for (String word : SPLIT.split(clean)) {
            String w = word.replaceAll("^[._-]+|[._-]+$", "");
            if (w.length() >= 3 && !STOP.contains(w)) {
                into.add(w);
            }
        }
    }

    static String normalize(String text) {
        String s = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return s.replaceAll("\\p{M}", "");
    }

    /**
     * Les incidents du corpus les plus proches de la requête, au-dessus d'un score minimal,
     * du plus proche au moins proche.
     */
    public static List<Match> rank(List<String> query, Map<Long, List<String>> corpus, int max, double minScore) {
        if (query.isEmpty() || corpus.isEmpty()) {
            return List.of();
        }
        // Fréquence documentaire de chaque mot (le corpus et la requête forment l'ensemble des documents).
        Map<String, Integer> df = new HashMap<>();
        List<List<String>> documents = new ArrayList<>(corpus.values());
        documents.add(query);
        for (List<String> doc : documents) {
            new HashSet<>(doc).forEach(t -> df.merge(t, 1, Integer::sum));
        }
        int n = documents.size();
        Map<String, Double> q = tfidf(query, df, n);
        List<Match> matches = new ArrayList<>();
        corpus.forEach((id, doc) -> {
            Map<String, Double> d = tfidf(doc, df, n);
            double score = cosine(q, d);
            if (score >= minScore) {
                matches.add(new Match(id, score, shared(q, d)));
            }
        });
        matches.sort(Comparator.comparingDouble(Match::score).reversed());
        return matches.subList(0, Math.min(max, matches.size()));
    }

    static Map<String, Double> tfidf(List<String> doc, Map<String, Integer> df, int n) {
        Map<String, Double> tf = new HashMap<>();
        doc.forEach(t -> tf.merge(t, 1.0, Double::sum));
        Map<String, Double> weights = new HashMap<>();
        tf.forEach((t, count) -> {
            // Lissage faible : un mot présent dans tous les documents ne pèse presque plus rien.
            double idf = Math.log((n + 1.0) / (df.getOrDefault(t, 0) + 1.0)) + IDF_FLOOR;
            weights.put(t, (count / doc.size()) * idf);
        });
        return weights;
    }

    static double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            Double other = b.get(e.getKey());
            if (other != null) {
                dot += e.getValue() * other;
            }
        }
        double na = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double nb = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        return na == 0 || nb == 0 ? 0 : dot / (na * nb);
    }

    /**
     * Les points communs, lisibles par un humain : d'abord les plus parlants (même cause, même version,
     * même étape), puis les mots partagés qui contribuent le plus à la similarité.
     */
    static List<String> shared(Map<String, Double> a, Map<String, Double> b) {
        List<String> result = new ArrayList<>();
        for (String prefix : new String[]{"cause:", "version:", "etape:"}) {
            if (a.keySet().stream().anyMatch(t -> t.startsWith(prefix) && b.containsKey(t))) {
                result.add(prefix.equals("cause:") ? "même cause" : prefix.equals("version:") ? "même version" : "même étape");
            }
        }
        a.keySet().stream()
                .filter(t -> b.containsKey(t) && !t.contains(":"))
                .sorted(Comparator.comparingDouble((String t) -> -a.get(t) * b.get(t)))
                .limit(Math.max(0, 4 - result.size()))
                .forEach(result::add);
        return result;
    }
}
