package com.smartdeploy.deployment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdeploy.confidence.ConfidenceCalculator;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentNotFoundException;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.guard.DeploymentGuard;
import com.smartdeploy.guard.DeploymentGuardRepository;
import com.smartdeploy.guard.GuardStatus;
import com.smartdeploy.incident.IncidentAnalysis;
import com.smartdeploy.incident.IncidentAnalysisRepository;
import com.smartdeploy.incident.IncidentFacts;
import com.smartdeploy.integration.github.GitHubClient;
import com.smartdeploy.version.Version;
import com.smartdeploy.version.VersionNotFoundException;
import com.smartdeploy.version.VersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ce qu'il faut savoir avant de déployer une version sur un environnement :
 * redéploiement, version plus ancienne, dégradation passée, échec répété, image réutilisée.
 */
@Service
@Transactional(readOnly = true)
public class DeploymentPrecheckService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Comparator<Deployment> BY_DATE =
            Comparator.comparing(Deployment::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final VersionRepository versionRepository;
    private final EnvironmentRepository environmentRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentGuardRepository guardRepository;
    private final IncidentAnalysisRepository incidentRepository;
    private final GitHubClient gitHub;

    public DeploymentPrecheckService(VersionRepository versionRepository, EnvironmentRepository environmentRepository,
                                     DeploymentRepository deploymentRepository, DeploymentGuardRepository guardRepository,
                                     IncidentAnalysisRepository incidentRepository, GitHubClient gitHub) {
        this.versionRepository = versionRepository;
        this.environmentRepository = environmentRepository;
        this.deploymentRepository = deploymentRepository;
        this.guardRepository = guardRepository;
        this.incidentRepository = incidentRepository;
        this.gitHub = gitHub;
    }

    public DeploymentPrecheck check(Long versionId, Long environmentId) {
        Version version = versionRepository.findByIdAndDeletedFalse(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));
        Environment environment = environmentRepository.findByIdAndDeletedFalse(environmentId)
                .orElseThrow(() -> new EnvironmentNotFoundException(environmentId));

        Deployment current = deploymentRepository
                .findFirstByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(environment, DeploymentStatus.SUCCESS)
                .orElse(null);
        boolean same = current != null && current.getVersion().getId().equals(version.getId());
        boolean older = current != null && !same
                && VersionOrder.isOlder(version.getVersion(), current.getVersion().getVersion());

        List<Deployment> history = deploymentRepository.findByVersion_IdAndDeletedFalse(versionId);
        Set<Long> degradedIds = history.isEmpty() ? Set.of()
                : guardRepository.findByDeploymentIdIn(history.stream().map(Deployment::getId).toList()).stream()
                        .filter(guard -> guard.getStatus() == GuardStatus.DEGRADED)
                        .map(DeploymentGuard::getDeploymentId)
                        .collect(Collectors.toSet());

        Long rollbackTarget = !older ? null : history.stream()
                .filter(d -> d.getEnvironment().getId().equals(environment.getId()))
                .filter(DeploymentPrecheckService::hasImages)
                .filter(d -> !d.getId().equals(current.getId()))
                .filter(d -> !degradedIds.contains(d.getId()))
                .max(BY_DATE)
                .map(Deployment::getId)
                .orElse(null);

        DeploymentPrecheck.Degraded degraded = history.stream()
                .filter(d -> d.getRollbackOf() == null && degradedIds.contains(d.getId()))
                .max(BY_DATE)
                .map(d -> new DeploymentPrecheck.Degraded(d.getId(), d.getEnvironment().getName(), d.getCreatedAt(),
                        incidentRepository.findFirstByDeploymentIdOrderByIdDesc(d.getId())
                                .map(IncidentAnalysis::getTitle)
                                .orElse("Dégradation détectée par le gardien")))
                .orElse(null);

        DeploymentPrecheck.Reuse reuse = history.stream()
                .filter(DeploymentPrecheckService::hasImages)
                .max(BY_DATE)
                .map(d -> new DeploymentPrecheck.Reuse(d.getId(), d.getEnvironment().getName(),
                        d.getBuiltCommit() != null ? d.getBuiltCommit() : version.getGitCommit()))
                .orElse(null);

        return new DeploymentPrecheck(
                current == null ? null : current.getId(),
                current == null ? null : current.getVersion().getVersion(),
                current == null ? null : (current.getFinishedAt() != null ? current.getFinishedAt() : current.getCreatedAt()),
                same, older, rollbackTarget, degraded, lastFailure(version, history), reuse, version.getGitCommit());
    }

    /** RG-V8 : le dernier échec de la version, s'il est postérieur à sa dernière réussite. */
    private DeploymentPrecheck.LastFailure lastFailure(Version version, List<Deployment> history) {
        Optional<Deployment> failure = history.stream().filter(d -> d.getStatus() == DeploymentStatus.FAILED).max(BY_DATE);
        if (failure.isEmpty()) {
            return null;
        }
        Deployment failed = failure.get();
        Optional<LocalDateTime> lastSuccess = history.stream()
                .filter(d -> d.getStatus() == DeploymentStatus.SUCCESS || d.getStatus() == DeploymentStatus.ROLLED_BACK)
                .map(Deployment::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder());
        if (lastSuccess.isPresent() && failed.getCreatedAt() != null && lastSuccess.get().isAfter(failed.getCreatedAt())) {
            return null;
        }
        IncidentAnalysis analysis = incidentRepository.findFirstByDeploymentIdOrderByIdDesc(failed.getId()).orElse(null);
        boolean codeCause = analysis != null && ConfidenceCalculator.isVersionCause(analysis.getCode());
        String title = analysis != null ? analysis.getTitle() : "Échec au déploiement";
        String detail = detail(analysis, failed);

        Boolean unchanged = null;
        String unverifiableReason = null;
        if (codeCause) {
            String failedCommit = failed.getBuiltCommit();
            String commitNow = version.getGitCommit() != null ? version.getGitCommit()
                    : gitHub.headCommit(version.getApplication().getRepositoryUrl(), version.getGitBranch()).orElse(null);
            if (commitNow != null && failedCommit != null) {
                unchanged = commitNow.startsWith(failedCommit) || failedCommit.startsWith(commitNow);
            } else if (version.getGitCommit() != null) {
                unchanged = Boolean.TRUE;   // commit fixé : le même code sera construit
            } else if (failedCommit == null) {
                unverifiableReason = "le commit construit lors de l'échec est inconnu (déploiement antérieur au suivi des commits)";
            } else {
                unverifiableReason = "le dernier commit de la branche n'a pas pu être lu sur GitHub";
            }
        }
        return new DeploymentPrecheck.LastFailure(failed.getId(), failed.getCreatedAt(), title, detail, codeCause, unchanged,
                unverifiableReason);
    }

    private static String detail(IncidentAnalysis analysis, Deployment failed) {
        if (analysis != null && analysis.getFactsJson() != null) {
            try {
                List<IncidentFacts.Fact> facts = MAPPER.readValue(analysis.getFactsJson(), new TypeReference<List<IncidentFacts.Fact>>() { });
                if (!facts.isEmpty()) {
                    return facts.stream().map(f -> f.label() + " : " + f.value()).collect(Collectors.joining(" · "));
                }
            } catch (Exception ignored) {
                // faits illisibles : on passe à la ligne décisive
            }
        }
        if (analysis != null && analysis.getDecisiveLine() != null) {
            return analysis.getDecisiveLine();
        }
        return failed.getErrorMessage();
    }

    private static boolean hasImages(Deployment d) {
        return (d.getStatus() == DeploymentStatus.SUCCESS || d.getStatus() == DeploymentStatus.ROLLED_BACK)
                && d.getDeployedBackendImage() != null && d.getDeployedBackendImage().contains("@sha256:");
    }
}
