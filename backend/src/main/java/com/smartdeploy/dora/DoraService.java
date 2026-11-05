package com.smartdeploy.dora;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.guard.DeploymentGuard;
import com.smartdeploy.guard.DeploymentGuardRepository;
import com.smartdeploy.guard.GuardStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Indicateurs DORA, calculés à partir de l'historique des déploiements et des
 * verdicts du gardien : aucune donnée supplémentaire n'est nécessaire.
 */
@Service
@Transactional(readOnly = true)
public class DoraService {

    /** Versions du banc d'essai : pannes provoquées volontairement. */
    static final String BENCH_BRANCH_PREFIX = "panne/";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentGuardRepository guardRepository;

    public DoraService(DeploymentRepository deploymentRepository, DeploymentGuardRepository guardRepository) {
        this.deploymentRepository = deploymentRepository;
        this.guardRepository = guardRepository;
    }

    public DoraReport compute(int days, Long applicationId, Long environmentId, boolean excludeBench) {
        int period = Math.min(Math.max(days, 1), 365);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(period);

        List<Deployment> deployments = deploymentRepository.findByCreatedAtAfterAndDeletedFalse(from).stream()
                .filter(d -> applicationId == null || d.getVersion().getApplication().getId().equals(applicationId))
                .filter(d -> environmentId == null || d.getEnvironment().getId().equals(environmentId))
                .toList();
        long bench = deployments.stream().filter(DoraService::isBench).count();
        if (excludeBench) {
            deployments = deployments.stream().filter(d -> !isBench(d)).toList();
        }

        Set<Long> degraded = deployments.isEmpty() ? Set.of()
                : guardRepository.findByDeploymentIdIn(deployments.stream().map(Deployment::getId).toList()).stream()
                .filter(guard -> guard.getStatus() == GuardStatus.DEGRADED)
                .map(DeploymentGuard::getDeploymentId)
                .collect(Collectors.toSet());

        List<DeploymentFact> facts = deployments.stream().map(d -> new DeploymentFact(
                d.getId(),
                d.getEnvironment().getId(),
                d.getStatus() == DeploymentStatus.SUCCESS || d.getStatus() == DeploymentStatus.ROLLED_BACK ? "SUCCESS"
                        : d.getStatus() == DeploymentStatus.FAILED ? "FAILED" : "OTHER",
                d.getRollbackOf() != null,
                degraded.contains(d.getId()),
                d.getCreatedAt(),
                d.getFinishedAt()
        )).toList();

        List<String> notes = new ArrayList<>();
        notes.add("Délai de livraison mesuré depuis la demande de déploiement : la date du commit n'est pas enregistrée.");
        notes.add(excludeBench
                ? bench + " déploiement(s) du banc d'essai (pannes volontaires) exclu(s) du calcul."
                : bench + " déploiement(s) du banc d'essai (pannes volontaires) inclus dans le calcul.");
        notes.add("Niveaux : repères indicatifs des rapports DORA (Accelerate State of DevOps), variables selon les années.");
        return new DoraReport(period, from, to, DoraCalculator.compute(facts, period), notes);
    }

    static boolean isBench(Deployment deployment) {
        String branch = deployment.getVersion().getGitBranch();
        return branch != null && branch.startsWith(BENCH_BRANCH_PREFIX);
    }
}
