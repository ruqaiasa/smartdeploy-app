package com.smartdeploy.deployment;

import com.smartdeploy.application.ApplicationAccess;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RG-O1 appliqué aux lectures d'un déploiement par son numéro (détail, analyse, gardien, métriques,
 * promotion) : un développeur ne lit que les déploiements de ses applications.
 */
@Component
public class DeploymentReadAccess {

    private final DeploymentRepository deployments;

    public DeploymentReadAccess(DeploymentRepository deployments) {
        this.deployments = deployments;
    }

    @Transactional(readOnly = true)
    public void ensureCanRead(Long deploymentId) {
        if (!ApplicationAccess.isRestricted(ApplicationAccess.authenticatedUser())) {
            return;   // DevOps, administrateur, lecteur, ou traitement du système
        }
        deployments.findById(deploymentId).ifPresent(d ->
                ApplicationAccess.ensureCurrentUserCanAccess(d.getVersion().getApplication()));
    }
}
