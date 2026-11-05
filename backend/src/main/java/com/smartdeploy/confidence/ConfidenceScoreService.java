package com.smartdeploy.confidence;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.environment.EnvironmentType;
import com.smartdeploy.guard.DeploymentGuardRepository;
import com.smartdeploy.guard.GuardStatus;
import com.smartdeploy.incident.IncidentAnalysisRepository;
import com.smartdeploy.version.Version;
import com.smartdeploy.version.VersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Rassemble, dans l'historique de SmartDeploy, les faits utiles au score de
 * confiance d'un déploiement à venir, puis le calcule.
 */
@Service
@Transactional(readOnly = true)
public class ConfidenceScoreService {

    private static final Set<DeploymentStatus> FINISHED =
            Set.of(DeploymentStatus.SUCCESS, DeploymentStatus.FAILED, DeploymentStatus.ROLLED_BACK);
    private static final Set<EnvironmentType> VALIDATION_ENVIRONMENTS = Set.of(EnvironmentType.DEVELOPMENT, EnvironmentType.TEST);

    private final DeploymentRepository deploymentRepository;
    private final VersionRepository versionRepository;
    private final EnvironmentRepository environmentRepository;
    private final DeploymentGuardRepository guardRepository;
    private final IncidentAnalysisRepository incidentRepository;

    public ConfidenceScoreService(DeploymentRepository deploymentRepository, VersionRepository versionRepository,
                                  EnvironmentRepository environmentRepository, DeploymentGuardRepository guardRepository,
                                  IncidentAnalysisRepository incidentRepository) {
        this.deploymentRepository = deploymentRepository;
        this.versionRepository = versionRepository;
        this.environmentRepository = environmentRepository;
        this.guardRepository = guardRepository;
        this.incidentRepository = incidentRepository;
    }

    public Optional<ConfidenceScore> compute(Long versionId, Long environmentId) {
        Optional<Version> version = versionRepository.findById(versionId);
        Optional<Environment> environment = environmentRepository.findById(environmentId);
        if (version.isEmpty() || environment.isEmpty()) {
            return Optional.empty();
        }

        List<Deployment> recent = deploymentRepository.findTop10ByEnvironment_IdAndDeletedFalseOrderByCreatedAtDesc(environmentId)
                .stream().filter(d -> FINISHED.contains(d.getStatus())).toList();
        List<Deployment> versionHistory = deploymentRepository.findByVersion_IdAndDeletedFalse(versionId);
        List<Long> versionIds = versionHistory.stream().map(Deployment::getId).toList();

        boolean degraded = !versionIds.isEmpty() && guardRepository.findByDeploymentIdIn(versionIds).stream()
                .anyMatch(guard -> guard.getStatus() == GuardStatus.DEGRADED);
        int versionFailures = (int) versionHistory.stream().filter(d -> d.getStatus() == DeploymentStatus.FAILED).count();
        boolean validated = versionHistory.stream()
                .anyMatch(d -> (d.getStatus() == DeploymentStatus.SUCCESS || d.getStatus() == DeploymentStatus.ROLLED_BACK)
                        && VALIDATION_ENVIRONMENTS.contains(d.getEnvironment().getType()));
        List<Long> recentIds = recent.stream().map(Deployment::getId).toList();
        int incidents = recentIds.isEmpty() ? 0
                : (int) incidentRepository.countIncidentDeployments(recentIds, LocalDateTime.now().minusHours(24));

        return Optional.of(ConfidenceCalculator.compute(new ConfidenceInputs(
                recent.size(),
                (int) recent.stream().filter(d -> d.getStatus() == DeploymentStatus.FAILED).count(),
                versionFailures,
                degraded,
                incidents,
                validated,
                environment.get().getType(),
                version.get().getGitBranch()
        )));
    }
}
