package com.smartdeploy.incident;

import com.smartdeploy.incident.IncidentDiagnosis.Confidence;
import com.smartdeploy.incident.IncidentEvidence.Source;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Compare les preuves au catalogue des pannes connues et retient la
 * signature la mieux étayée. Le diagnostic cite toujours les lignes qui le
 * justifient ; sans correspondance, il le dit et présente les lignes d'erreur
 * brutes plutôt que d'inventer une cause.
 */
public final class IncidentAnalyzer {

    static final int MAX_EVIDENCE = 6;
    private static final Pattern ERROR_LINE =
            Pattern.compile("(ERROR|Exception|FAILED|FAILURE|error TS|fatal|refused|denied|BackOff)", Pattern.CASE_INSENSITIVE);

    private IncidentAnalyzer() {
    }

    public static IncidentDiagnosis analyze(IncidentEvidence evidence) {
        List<Match> matches = new ArrayList<>();
        for (IncidentRule rule : IncidentRuleCatalog.RULES) {
            Match match = score(rule, evidence);
            if (match.score() > 0) {
                matches.add(match);
            }
        }
        // Une cause identifiée passe avant un symptôme (plantage en boucle, sondes),
        // puis les correspondances sont classées par score.
        matches.sort(Comparator.comparing((Match m) -> m.rule().symptom())
                .thenComparing(Comparator.comparingInt(Match::score).reversed()));

        if (matches.isEmpty() || matches.get(0).lines().isEmpty()) {
            return unknown(evidence);
        }

        Match best = matches.get(0);
        // Confiance élevée : l'étape attendue est en échec, ou plusieurs motifs
        // distincts de la signature sont retrouvés (cas d'une panne détectée
        // par le gardien, sans étape de pipeline).
        Confidence confidence = best.stageMatched() || best.patternsFound() >= 2 ? Confidence.HIGH
                : best.lines().size() >= 2 ? Confidence.MEDIUM : Confidence.LOW;
        List<String> evidenceLines = new ArrayList<>();
        if (evidence.failedStage() != null) {
            evidenceLines.add(Source.STAGE.label() + " : " + evidence.failedStage());
        }
        evidenceLines.addAll(best.lines());
        List<String> alternatives = matches.stream().skip(1)
                .filter(m -> !m.lines().isEmpty())
                .limit(2)
                .map(m -> m.rule().title())
                .toList();

        return new IncidentDiagnosis(best.rule().code(), best.rule().title(), confidence,
                best.rule().explanation(), best.rule().action(),
                evidenceLines.stream().limit(MAX_EVIDENCE + 1L).toList(), alternatives);
    }

    private record Match(IncidentRule rule, int score, int patternsFound, boolean stageMatched, List<String> lines) {
    }

    private static Match score(IncidentRule rule, IncidentEvidence evidence) {
        boolean stageMatched = evidence.failedStage() != null && rule.stage() != null
                && rule.stage().matcher(evidence.failedStage()).find();
        // Quand l'étape en échec est connue, une signature de construction
        // (log Jenkins) propre à une autre étape ne peut pas être retenue :
        // ses motifs proviendraient d'étapes qui ont réussi.
        if (evidence.failedStage() != null && !stageMatched && rule.sources().contains(Source.JENKINS)
                && !rule.sources().contains(Source.POD)) {
            return new Match(rule, 0, 0, false, List.of());
        }
        Set<String> lines = new LinkedHashSet<>();
        int patternsFound = 0;
        for (Pattern pattern : rule.patterns()) {
            boolean found = false;
            for (Source source : rule.sources()) {
                for (String line : evidence.lines(source)) {
                    if (pattern.matcher(line).find()) {
                        found = true;
                        if (lines.size() < MAX_EVIDENCE) {
                            lines.add(source.label() + " : " + abbreviate(line.strip()));
                        }
                    }
                }
            }
            if (found) {
                patternsFound++;
            }
        }
        // Chaque motif distinct compte double ; l'étape attendue départage les signatures proches.
        int score = patternsFound * 2 + (stageMatched && patternsFound > 0 ? 3 : 0);
        return new Match(rule, score, patternsFound, stageMatched, List.copyOf(lines));
    }

    private static IncidentDiagnosis unknown(IncidentEvidence evidence) {
        List<String> lines = new ArrayList<>();
        if (evidence.failedStage() != null) {
            lines.add(Source.STAGE.label() + " : " + evidence.failedStage());
        }
        if (evidence.errorMessage() != null) {
            lines.add(Source.ERROR.label() + " : " + abbreviate(evidence.errorMessage()));
        }
        for (Source source : List.of(Source.CONTAINER, Source.EVENT, Source.POD, Source.JENKINS)) {
            for (String line : evidence.lines(source)) {
                if (lines.size() >= MAX_EVIDENCE) {
                    break;
                }
                if (ERROR_LINE.matcher(line).find()) {
                    lines.add(source.label() + " : " + abbreviate(line.strip()));
                }
            }
        }
        return new IncidentDiagnosis(IncidentDiagnosis.UNKNOWN, "Cause non identifiée", Confidence.LOW,
                "Aucune signature de panne connue ne correspond aux preuves rassemblées. Les lignes d'erreur brutes sont présentées ci-dessous.",
                "Examiner les preuves ci-dessous et le log complet du build ; si la panne se reproduit, ajouter sa signature au catalogue.",
                lines, List.of());
    }

    static String abbreviate(String line) {
        return line.length() <= 240 ? line : line.substring(0, 237) + "…";
    }
}
