package com.smartdeploy.backup;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Faut-il un point de restauration avant ce déploiement ? Oui seulement s'il
 * y a quelque chose à protéger : une version en service dans l'environnement.
 * Jamais avant un retour arrière : c'est une remédiation, elle doit rester rapide
 * (l'état antérieur reste couvert par les sauvegardes existantes).
 */
@Component
public class RestorePointPolicy {

    private static final Logger log = LoggerFactory.getLogger(RestorePointPolicy.class);

    private final VeleroProperties properties;
    private final VeleroClient velero;
    private final DeploymentRepository deploymentRepository;

    public RestorePointPolicy(VeleroProperties properties, VeleroClient velero, DeploymentRepository deploymentRepository) {
        this.properties = properties;
        this.velero = velero;
        this.deploymentRepository = deploymentRepository;
    }

    public boolean required(Deployment deployment) {
        if (!properties.restorePointBeforeDeployment()) {
            return false;
        }
        if (deployment.getRollbackOf() != null) {
            return false;
        }
        try {
            boolean versionInService = deploymentRepository
                    .findFirstByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(deployment.getEnvironment(), DeploymentStatus.SUCCESS)
                    .isPresent();
            return versionInService && velero.namespaceExists(deployment.getEnvironment().getNamespace());
        } catch (RuntimeException exception) {
            log.warn("Point de restauration ignoré : cluster injoignable ({})", exception.getMessage());
            return false;
        }
    }
}
