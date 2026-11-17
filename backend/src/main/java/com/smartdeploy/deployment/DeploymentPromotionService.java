package com.smartdeploy.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.guard.DeploymentGuardRepository;
import com.smartdeploy.guard.GuardStatus;
import com.smartdeploy.integration.kubernetes.KubernetesApplicationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Optional;

/**
 * Promotion guidée (RG-P1, RG-P3, RG-P4) : l'environnement suivant d'un déploiement validé,
 * dans l'ordre DEVELOPMENT < TEST < STAGING < PREPRODUCTION < PRODUCTION, et sa configuration à reprendre.
 * La création elle-même passe par le chemin ordinaire (contrôles, image réutilisée, approbation).
 */
@Service
@Transactional(readOnly = true)
public class DeploymentPromotionService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeploymentRepository deploymentRepository;
    private final EnvironmentRepository environmentRepository;
    private final DeploymentKubernetesConfigRepository configRepository;
    private final DeploymentGuardRepository guardRepository;

    public DeploymentPromotionService(DeploymentRepository deploymentRepository, EnvironmentRepository environmentRepository,
                                      DeploymentKubernetesConfigRepository configRepository,
                                      DeploymentGuardRepository guardRepository) {
        this.deploymentRepository = deploymentRepository;
        this.environmentRepository = environmentRepository;
        this.configRepository = configRepository;
        this.guardRepository = guardRepository;
    }

    public PromotionProposal propose(Long deploymentId) {
        Deployment deployment = deploymentRepository.findByIdAndDeletedFalse(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));
        if (deployment.getStatus() != DeploymentStatus.SUCCESS && deployment.getStatus() != DeploymentStatus.ROLLED_BACK) {
            throw new DeploymentStateException("Seul un déploiement réussi peut être promu");
        }
        boolean validated = guardRepository.findFirstByDeploymentIdOrderByIdDesc(deployment.getId())
                .map(guard -> guard.getStatus() == GuardStatus.HEALTHY || guard.getStatus() == GuardStatus.HEALTHY_WITH_WARNINGS)
                .orElse(false);
        if (!validated) {
            throw new DeploymentStateException("Ce déploiement n'a pas été validé par le gardien : il ne peut pas encore être promu");
        }
        Environment current = deployment.getEnvironment();
        Long applicationId = deployment.getVersion().getApplication().getId();
        Environment target = nextEnvironment(applicationId, current)
                .orElseThrow(() -> new DeploymentStateException(
                        "Aucun environnement après " + current.getName() + " pour cette application : créez-en un pour promouvoir"));
        KubernetesApplicationRequest configuration = configRepository.findByDeploymentId(deployment.getId())
                .map(stored -> {
                    try {
                        return MAPPER.readValue(stored.getConfigurationJson(), KubernetesApplicationRequest.class);
                    } catch (Exception exception) {
                        return null;
                    }
                })
                .orElse(null);
        return new PromotionProposal(deployment.getId(), current.getName(), applicationId,
                deployment.getVersion().getId(), deployment.getVersion().getVersion(),
                target.getId(), target.getName(), target.getType().name(), configuration);
    }

    /** RG-P1 : l'environnement actif immédiatement supérieur, pour la même application. */
    Optional<Environment> nextEnvironment(Long applicationId, Environment current) {
        return environmentRepository.findAllByApplicationIdAndDeletedFalseOrderByCreatedAtDesc(applicationId).stream()
                .filter(Environment::isActive)
                .filter(env -> env.getType() != null && current.getType() != null
                        && env.getType().ordinal() > current.getType().ordinal())
                .min(Comparator.comparing(env -> env.getType().ordinal()));
    }
}
