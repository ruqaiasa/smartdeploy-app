package com.smartdeploy.confidence;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.environment.EnvironmentType;
import com.smartdeploy.guard.DeploymentGuardRepository;
import com.smartdeploy.guard.GuardStatus;
import com.smartdeploy.incident.IncidentAnalysis;
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
        // Un retour arrière jugé dégradé hérite souvent des pods défaillants de la version qu'il
        // remplace (cas réel du 4 novembre) : il ne pénalise pas la version remise en service.
        List<Long> versionIds = versionHistory.stream()
                .filter(d -> d.getRollbackOf() == null).map(Deployment::getId).toList();

        // Mises en service de cette version jugées dégradées par le gardien (une par déploiement)
        Set<Long> degradedIds = versionIds.isEmpty() ? Set.of() : guardRepository.findByDeploymentIdIn(versionIds).stream()
                .filter(guard -> guard.getStatus() == GuardStatus.DEGRADED)
                .map(guard -> guard.getDeploymentId())
                .collect(java.util.stream.Collectors.toSet());
        boolean degraded = !degradedIds.isEmpty();
        // Seuls les échecs dus au code de la version la pénalisent (diagnostic de l'analyste).
        Set<Long> codeFailureIds = versionHistory.stream()
                .filter(d -> d.getStatus() == DeploymentStatus.FAILED)
                .filter(d -> incidentRepository.findFirstByDeploymentIdOrderByIdDesc(d.getId())
                        .map(IncidentAnalysis::getCode)
                        .filter(ConfidenceCalculator::isVersionCause)
                        .isPresent())
                .map(Deployment::getId)
                .collect(java.util.stream.Collectors.toSet());
        int versionFailures = codeFailureIds.size();
        // Validée : réussie en développement ou en test, SANS dégradation constatée par le gardien
        // (une version retirée par un retour arrière automatique n'est pas validée).
        boolean validated = versionHistory.stream()
                .anyMatch(d -> (d.getStatus() == DeploymentStatus.SUCCESS || d.getStatus() == DeploymentStatus.ROLLED_BACK)
                        && VALIDATION_ENVIRONMENTS.contains(d.getEnvironment().getType())
                        && !degradedIds.contains(d.getId()));
        // Incidents : les mises en service dégradées. Les échecs du pipeline sont déjà comptés
        // ci-dessus : un même échec ne doit pas être pénalisé deux fois.
        // Les dégradations de cette version sont déjà comptées par « version déjà jugée dégradée ».
        List<Long> recentIds = recent.stream()
                .filter(d -> d.getStatus() != DeploymentStatus.FAILED)
                .filter(d -> !d.getVersion().getId().equals(versionId))
                .map(Deployment::getId).toList();
        int incidents = recentIds.isEmpty() ? 0
                : (int) incidentRepository.countIncidentDeployments(recentIds, LocalDateTime.now().minusHours(24));

        return Optional.of(ConfidenceCalculator.compute(new ConfidenceInputs(
                recent.size(),
                (int) recent.stream()
                        .filter(d -> d.getStatus() == DeploymentStatus.FAILED && !codeFailureIds.contains(d.getId()))
                        .count(),
                versionFailures,
                degraded,
                incidents,
                validated,
                environment.get().getType(),
                version.get().getGitBranch(),
                degradedIds.size()
        )));
    }
}
