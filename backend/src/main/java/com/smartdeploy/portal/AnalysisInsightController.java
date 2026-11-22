package com.smartdeploy.portal;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentReadAccess;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.incident.IncidentAnalysis;
import com.smartdeploy.incident.IncidentAnalysisRepository;
import com.smartdeploy.integration.github.GitHubClient;
import com.smartdeploy.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Analyste v2 : la corrélation avec le changement de code (un fichier modifié par la version en échec ET cité
 * dans l'erreur), le verdict « code » ou « environnement », le retour des DevOps et la précision de l'analyste.
 */
@RestController
public class AnalysisInsightController {

    public record Insight(String kind, String verdict, String baseVersion, String baseCommit, String headCommit,
                          List<String> changedFiles, List<String> suspectFiles, List<GitHubClient.CommitInfo> commits,
                          boolean sameVersionSucceeded, String feedback, List<KnowledgeService.ArticleResponse> suggestions) {
    }

    public record FeedbackRequest(AnalysisFeedback.Verdict verdict, String actualCause) {
    }

    public record CodeStat(String code, int total, int correct, int partial, int wrong) {
    }

    public record AnalystStats(int total, int correct, int partial, int wrong, Integer precision, List<CodeStat> byCode) {
    }

    private final DeploymentRepository deployments;
    private final IncidentAnalysisRepository analyses;
    private final AnalysisFeedbackRepository feedback;
    private final GitHubClient github;
    private final KnowledgeService knowledge;
    private final DeploymentReadAccess readAccess;

    public AnalysisInsightController(DeploymentRepository deployments, IncidentAnalysisRepository analyses,
                                     AnalysisFeedbackRepository feedback, GitHubClient github, KnowledgeService knowledge,
                                     DeploymentReadAccess readAccess) {
        this.deployments = deployments;
        this.analyses = analyses;
        this.feedback = feedback;
        this.github = github;
        this.knowledge = knowledge;
        this.readAccess = readAccess;
    }

    @GetMapping("/api/deployments/{id}/insight")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    @Transactional(readOnly = true)
    public Insight insight(@PathVariable Long id) {
        readAccess.ensureCanRead(id);
        Deployment d = deployments.findById(id).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Déploiement introuvable : " + id));
        Optional<IncidentAnalysis> analysis = analyses.findFirstByDeploymentIdOrderByIdDesc(id);
        List<Deployment> recent = deployments.findTop10ByEnvironment_IdAndDeletedFalseOrderByCreatedAtDesc(d.getEnvironment().getId());
        boolean sameVersionSucceeded = recent.stream().anyMatch(x -> !x.getId().equals(d.getId())
                && x.getStatus() == DeploymentStatus.SUCCESS && x.getVersion().getId().equals(d.getVersion().getId()));
        Optional<Deployment> base = recent.stream()
                .filter(x -> !x.getId().equals(d.getId()) && x.getStatus() == DeploymentStatus.SUCCESS && x.getBuiltCommit() != null
                        && x.getCreatedAt() != null && d.getCreatedAt() != null && x.getCreatedAt().isBefore(d.getCreatedAt()))
                .max(Comparator.comparing(Deployment::getCreatedAt));
        String head = d.getBuiltCommit() != null ? d.getBuiltCommit() : d.getVersion().getGitCommit();
        String baseCommit = base.map(Deployment::getBuiltCommit).orElse(null);
        Optional<GitHubClient.Comparison> comparison = baseCommit != null && head != null && !baseCommit.equals(head)
                ? github.compare(d.getVersion().getApplication().getRepositoryUrl(), baseCommit, head) : Optional.empty();
        List<String> changed = comparison.map(GitHubClient.Comparison::files).orElse(List.of());
        String text = analysis.map(AnalysisInsightController::analysisText).orElse("");
        List<String> suspects = suspects(changed, text);

        String kind;
        String verdict;
        if (analysis.isEmpty()) {
            kind = "NONE";
            verdict = "Aucune analyse pour ce déploiement.";
        } else if (!suspects.isEmpty()) {
            kind = "CODE";
            verdict = "La panne vient très probablement du changement de code : " + String.join(", ", suspects)
                    + (suspects.size() > 1 ? " ont été modifiés" : " a été modifié") + " par cette version et "
                    + (suspects.size() > 1 ? "sont cités" : "est cité") + " dans l'erreur.";
        } else if (sameVersionSucceeded) {
            kind = "ENVIRONMENT";
            verdict = "Cette même version fonctionnait déjà dans cet environnement : la cause est à chercher dans l'environnement"
                    + " (ressources, base de données, configuration), pas dans le code.";
        } else if (!changed.isEmpty()) {
            kind = "CODE_UNCERTAIN";
            verdict = "Cette version modifie " + changed.size() + " fichier" + (changed.size() > 1 ? "s" : "")
                    + " depuis la dernière version saine (" + base.map(x -> x.getVersion().getVersion()).orElse("?")
                    + "), mais aucun n'est cité dans l'erreur.";
        } else {
            kind = "UNKNOWN";
            verdict = "Impossible de comparer avec une version saine : aucune version précédente réussie ici, ou dépôt injoignable.";
        }
        String latestFeedback = feedback.findFirstByDeploymentIdOrderByIdDesc(id).map(f -> f.getVerdict().name()).orElse(null);
        List<KnowledgeService.ArticleResponse> suggestions = analysis
                .map(a -> knowledge.suggest(a.getTitle() + " " + (a.getDecisiveLine() == null ? "" : a.getDecisiveLine())))
                .orElse(List.of());
        List<GitHubClient.CommitInfo> commits = comparison.map(GitHubClient.Comparison::commits).orElse(List.of());
        return new Insight(kind, verdict, base.map(x -> x.getVersion().getVersion()).orElse(null), baseCommit, head,
                changed.size() > 30 ? changed.subList(0, 30) : changed, suspects,
                commits.size() > 10 ? commits.subList(commits.size() - 10, commits.size()) : commits,
                sameVersionSucceeded, latestFeedback, suggestions);
    }

    @PostMapping("/api/deployments/{id}/analysis/feedback")
    @PreAuthorize("hasAnyRole('DEVOPS', 'ADMIN')")
    @Transactional
    public void feedback(@PathVariable Long id, @RequestBody FeedbackRequest request, @AuthenticationPrincipal User user) {
        if (request == null || request.verdict() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indiquez si l'analyse était juste, partielle ou fausse");
        }
        AnalysisFeedback f = new AnalysisFeedback();
        f.setDeploymentId(id);
        analyses.findFirstByDeploymentIdOrderByIdDesc(id).ifPresent(a -> {
            f.setAnalysisId(a.getId());
            f.setCode(a.getCode());
        });
        f.setVerdict(request.verdict());
        f.setActualCause(request.actualCause());
        f.setAuthorEmail(user == null ? null : user.getEmail());
        f.setCreatedAt(LocalDateTime.now());
        feedback.save(f);
    }

    @GetMapping("/api/portal/analyst-stats")
    @PreAuthorize("hasAnyRole('DEVOPS', 'ADMIN')")
    @Transactional(readOnly = true)
    public AnalystStats stats() {
        Map<Long, AnalysisFeedback> latest = new TreeMap<>();
        for (AnalysisFeedback f : feedback.findAll()) {
            AnalysisFeedback kept = latest.get(f.getDeploymentId());
            if (kept == null || f.getId() > kept.getId()) {
                latest.put(f.getDeploymentId(), f);
            }
        }
        return statsOf(new ArrayList<>(latest.values()));
    }

    static AnalystStats statsOf(List<AnalysisFeedback> items) {
        int correct = 0, partial = 0, wrong = 0;
        Map<String, int[]> byCode = new TreeMap<>();
        for (AnalysisFeedback f : items) {
            int[] c = byCode.computeIfAbsent(f.getCode() == null ? "INCONNU" : f.getCode(), k -> new int[3]);
            switch (f.getVerdict()) {
                case CORRECT -> { correct++; c[0]++; }
                case PARTIAL -> { partial++; c[1]++; }
                default -> { wrong++; c[2]++; }
            }
        }
        int total = items.size();
        Integer precision = total == 0 ? null : (int) Math.round((correct + 0.5 * partial) * 100.0 / total);
        List<CodeStat> codes = new ArrayList<>();
        byCode.forEach((code, c) -> codes.add(new CodeStat(code, c[0] + c[1] + c[2], c[0], c[1], c[2])));
        return new AnalystStats(total, correct, partial, wrong, precision, codes);
    }

    static String analysisText(IncidentAnalysis a) {
        return String.join(" ", nz(a.getTitle()), nz(a.getExplanation()), nz(a.getAction()), nz(a.getDecisiveLine()),
                nz(a.getEvidenceJson()), nz(a.getFactsJson())).toLowerCase(Locale.ROOT);
    }

    /** Fichiers modifiés par la version ET cités dans l'erreur (par leur nom, sans le chemin). */
    static List<String> suspects(List<String> changed, String analysisText) {
        List<String> found = new ArrayList<>();
        for (String path : changed) {
            String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            if (name.length() > 3 && analysisText.contains(name)) {
                found.add(path);
            }
        }
        return found;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
