package com.smartdeploy.version;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.guard.DeploymentGuard;
import com.smartdeploy.guard.DeploymentGuardRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Ce qu'il faut savoir d'une version d'un coup d'œil (RG-V9, RG-V10) : où elle est en
 * service, si un déploiement est en cours, et le verdict de son dernier déploiement.
 */
@Component
@Transactional(readOnly = true)
public class VersionUsage {

    public record Verdict(String label, String tone) {
    }

    private final DeploymentRepository deployments;
    private final DeploymentGuardRepository guards;

    public VersionUsage(DeploymentRepository deployments, DeploymentGuardRepository guards) {
        this.deployments = deployments;
        this.guards = guards;
    }

    /** Environnements où cette version est la version en service (le dernier déploiement réussi). */
    public List<String> inServiceEnvironments(Long versionId) {
        return deployments.findByVersion_IdAndDeletedFalse(versionId).stream()
                .filter(d -> d.getStatus() == DeploymentStatus.SUCCESS)
                .filter(d -> deployments
                        .findFirstByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(d.getEnvironment(), DeploymentStatus.SUCCESS)
                        .map(latest -> latest.getId().equals(d.getId()))
                        .orElse(false))
                .map(d -> d.getEnvironment().getName())
                .distinct()
                .toList();
    }

    /** Un déploiement de cette version est-il en attente ou en cours ? */
    public boolean hasActiveDeployment(Long versionId) {
        return deployments.existsByVersion_IdAndStatusInAndDeletedFalse(versionId,
                List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING));
    }

    /** Le verdict du dernier déploiement de la version (hors annulations). */
    public Verdict lastVerdict(Long versionId) {
        Optional<Deployment> latest = deployments.findByVersion_IdAndDeletedFalse(versionId).stream()
                .filter(d -> d.getStatus() != DeploymentStatus.CANCELLED)
                .max(Comparator.comparing(Deployment::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        if (latest.isEmpty()) {
            return new Verdict("Jamais déployée", "muted");
        }
        Deployment d = latest.get();
        return switch (d.getStatus()) {
            case PENDING, RUNNING -> new Verdict("Déploiement en cours", "pending");
            case FAILED -> new Verdict("Échec au déploiement", "ko");
            default -> guardVerdict(d);
        };
    }

    private Verdict guardVerdict(Deployment deployment) {
        Optional<DeploymentGuard> guard = guards.findFirstByDeploymentIdOrderByIdDesc(deployment.getId());
        if (guard.isEmpty()) {
            return new Verdict("Mise en service", "ok");
        }
        return switch (guard.get().getStatus()) {
            case WATCHING -> new Verdict("Sous surveillance du gardien", "pending");
            case HEALTHY -> new Verdict("Validée par le gardien", "ok");
            case HEALTHY_WITH_WARNINGS -> new Verdict("Validée avec réserves", "warning");
            case DEGRADED -> new Verdict("Jugée dégradée par le gardien", "ko");
            case INCONCLUSIVE -> new Verdict("Verdict non concluant", "muted");
            case SUPERSEDED -> new Verdict("Mise en service", "ok");
        };
    }
}
