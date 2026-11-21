package com.smartdeploy.incident;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.guard.DeploymentGuard;
import com.smartdeploy.guard.DeploymentGuardRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mémoire des incidents : retrouve les incidents passés les plus proches d'un incident, par
 * similarité TF-IDF, puis ce qui s'est passé ensuite dans le même environnement (retour arrière
 * automatique ou manuel, nouvelle version réussie) et en combien de temps. Plus la plateforme
 * est utilisée, plus ses recommandations s'appuient sur l'expérience réelle de l'équipe.
 */
@Component
@Transactional(readOnly = true)
public class IncidentMemory {

    static final int CORPUS_SIZE = 500;
    static final int MAX_RESULTS = 3;
    static final int CANDIDATES = 10;
    static final double MIN_SCORE = 0.30;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IncidentAnalysisRepository analysisRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentGuardRepository guardRepository;

    public IncidentMemory(IncidentAnalysisRepository analysisRepository, DeploymentRepository deploymentRepository,
                          DeploymentGuardRepository guardRepository) {
        this.analysisRepository = analysisRepository;
        this.deploymentRepository = deploymentRepository;
        this.guardRepository = guardRepository;
    }

    public List<IncidentAnalysisResponse.SimilarIncident> similar(IncidentAnalysis current) {
        // Un incident = un déploiement. Son empreinte réunit sa première analyse (faite au moment de
        // l'incident, la plus riche : les pods remplacés emportent ensuite leurs logs) et sa dernière.
        Map<Long, IncidentAnalysis> latestByDeployment = new LinkedHashMap<>();
        Map<Long, IncidentAnalysis> firstByDeployment = new HashMap<>();
        for (IncidentAnalysis a : analysisRepository.findTop500ByOrderByIdDesc()) {
            if (!Objects.equals(a.getDeploymentId(), current.getDeploymentId())) {
                latestByDeployment.putIfAbsent(a.getDeploymentId(), a);
                firstByDeployment.put(a.getDeploymentId(), a);   // ordre décroissant : la dernière vue est la première faite
            }
        }
        // La version fait partie de l'empreinte : deux incidents de la même version ont souvent la même origine.
        // Le nom de l'application est retiré : tous ses incidents le partagent, il ne distingue rien.
        Map<Long, Deployment> deployments = new HashMap<>();
        deploymentRepository.findAllById(latestByDeployment.keySet()).forEach(d -> deployments.put(d.getId(), d));
        Map<Long, List<String>> corpus = new LinkedHashMap<>();
        latestByDeployment.forEach((id, latest) -> corpus.put(id,
                contextualize(tokens(firstByDeployment.get(id), latest), deployments.get(id))));
        IncidentAnalysis currentFirst = analysisRepository.findFirstByDeploymentIdOrderByIdAsc(current.getDeploymentId()).orElse(current);
        Optional<Deployment> currentDeployment = deploymentRepository.findById(current.getDeploymentId());
        List<String> query = contextualize(tokens(currentFirst, current), currentDeployment.orElse(null));
        List<IncidentSimilarity.Match> matches = IncidentSimilarity.rank(query, corpus, CANDIDATES, MIN_SCORE);

        Long currentEnvironmentId = currentDeployment.map(d -> d.getEnvironment().getId()).orElse(null);
        List<Ranked> ranked = new ArrayList<>();
        for (IncidentSimilarity.Match m : matches) {
            IncidentAnalysis past = latestByDeployment.get(m.id());
            deploymentRepository.findById(m.id()).ifPresent(d -> ranked.add(new Ranked(m, past, d,
                    (int) Math.round(m.score() * 100), Objects.equals(d.getEnvironment().getId(), currentEnvironmentId))));
        }
        // À score égal : d'abord le même environnement, puis le plus récent.
        ranked.sort(Comparator.comparingInt(Ranked::similarity).reversed()
                .thenComparing(Ranked::sameEnvironment, Comparator.reverseOrder())
                .thenComparing(r -> r.deployment().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())));
        List<IncidentAnalysisResponse.SimilarIncident> result = new ArrayList<>();
        for (Ranked r : ranked.subList(0, Math.min(MAX_RESULTS, ranked.size()))) {
            Deployment d = r.deployment();
            Resolution resolution = resolution(d);
            result.add(new IncidentAnalysisResponse.SimilarIncident(
                    d.getId(), d.getVersion().getVersion(), d.getEnvironment().getName(), d.getCreatedAt(),
                    r.similarity(), r.past().getTitle(), Objects.equals(r.past().getCode(), current.getCode()),
                    r.match().sharedTerms(), resolution.description(), resolution.minutes()));
        }
        return result;
    }

    record Resolution(String description, Long minutes) {
    }

    private record Ranked(IncidentSimilarity.Match match, IncidentAnalysis past, Deployment deployment,
                          int similarity, boolean sameEnvironment) {
    }

    /** Ce qui a remis l'environnement en état après cet incident, et en combien de temps. */
    Resolution resolution(Deployment incident) {
        // 1. Retour arrière automatique lancé par le gardien ?
        Optional<DeploymentGuard> guard = guardRepository.findFirstByDeploymentIdOrderByIdDesc(incident.getId());
        if (guard.isPresent() && guard.get().getAutoRollbackDeploymentId() != null) {
            Optional<Deployment> rollback = deploymentRepository.findById(guard.get().getAutoRollbackDeploymentId());
            if (rollback.isPresent() && succeeded(rollback.get())) {
                return new Resolution("Retour arrière automatique vers la version " + rollback.get().getVersion().getVersion(),
                        minutes(incident, rollback.get()));
            }
        }
        // 2. Sinon, le premier déploiement réussi qui a suivi dans le même environnement.
        for (Deployment next : deploymentRepository.findTop10ByEnvironmentAndCreatedAtAfterAndDeletedFalseOrderByCreatedAtAsc(
                incident.getEnvironment(), incident.getCreatedAt())) {
            if (!succeeded(next)) {
                continue;
            }
            String version = next.getVersion().getVersion();
            String how = next.getRollbackOf() != null ? "Retour arrière manuel vers la version " + version
                    : Objects.equals(next.getVersion().getId(), incident.getVersion().getId()) ? "Redéploiement de la même version " + version
                    : com.smartdeploy.deployment.VersionOrder.isOlder(version, incident.getVersion().getVersion())
                            ? "Retour à la version " + version + ", plus ancienne"
                            : "Nouvelle version " + version + " déployée avec succès";
            return new Resolution(how, minutes(incident, next));
        }
        return new Resolution("Pas encore résolu dans cet environnement", null);
    }

    private static boolean succeeded(Deployment d) {
        return d.getStatus() == DeploymentStatus.SUCCESS || d.getStatus() == DeploymentStatus.ROLLED_BACK;
    }

    private static Long minutes(Deployment incident, Deployment fix) {
        LocalDateTime end = fix.getFinishedAt() != null ? fix.getFinishedAt() : fix.getCreatedAt();
        if (incident.getCreatedAt() == null || end == null) {
            return null;
        }
        return Math.max(0, Duration.between(incident.getCreatedAt(), end).toMinutes());
    }

    /** Ajoute la version à l'empreinte et retire les mots formés sur le nom de l'application. */
    static List<String> contextualize(List<String> tokens, Deployment deployment) {
        if (deployment == null) {
            return tokens;
        }
        String app = IncidentSimilarity.normalize(deployment.getVersion().getApplication().getName()).replaceAll("[^a-z0-9-]+", "-");
        List<String> result = new ArrayList<>(tokens.stream().filter(t -> app.isBlank() || !t.startsWith(app)).toList());
        String version = deployment.getVersion().getVersion();
        if (version != null && !version.isBlank()) {
            // Comptée deux fois, comme la cause : c'est l'indice le plus fiable d'une même origine.
            result.add("version:" + version.trim().toLowerCase(Locale.ROOT));
            result.add("version:" + version.trim().toLowerCase(Locale.ROOT));
        }
        return result;
    }

    /** Empreinte d'un incident : sa première analyse et, si elle est différente, sa dernière. */
    static List<String> tokens(IncidentAnalysis first, IncidentAnalysis latest) {
        List<String> tokens = new ArrayList<>(tokens(latest));
        if (first != null && !Objects.equals(first.getId(), latest.getId())) {
            // La cause n'est comptée qu'une fois de plus : les mots des preuves enrichissent l'empreinte.
            tokens.addAll(tokens(first).stream().filter(t -> !t.startsWith("cause:")).toList());
        }
        return tokens;
    }

    /** Mots-clés d'une analyse : cause, étape en échec, faits, ligne décisive et preuves (le titre répète la cause). */
    static List<String> tokens(IncidentAnalysis a) {
        String stage = list(a.getEvidenceJson()).stream()
                .filter(e -> e.startsWith("Étape en échec"))
                .map(e -> e.substring(e.indexOf(':') + 1).trim())
                .findFirst().orElse(null);
        String facts = facts(a.getFactsJson()).stream()
                .map(f -> f.label() + " " + f.value())
                .collect(Collectors.joining(" "));
        // Les preuves (événements, état des pods) portent les indices propres à l'incident.
        String evidence = String.join(" ", list(a.getEvidenceJson()));
        return IncidentSimilarity.tokens(a.getCode(), null, stage, facts + " " + evidence, a.getDecisiveLine());
    }

    private static List<String> list(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return MAPPER.readValue(json, new TypeReference<List<String>>() { }); } catch (Exception e) { return List.of(); }
    }

    private static List<IncidentFacts.Fact> facts(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return MAPPER.readValue(json, new TypeReference<List<IncidentFacts.Fact>>() { }); } catch (Exception e) { return List.of(); }
    }
}
