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
    private final com.smartdeploy.version.VersionRepository versions;

    public DeploymentReadAccess(DeploymentRepository deployments, com.smartdeploy.version.VersionRepository versions) {
        this.deployments = deployments;
        this.versions = versions;
    }

    /** RG-O1 : un développeur n'interroge (vérifications, score) que les versions de ses applications. */
    @Transactional(readOnly = true)
    public void ensureCanReadVersion(Long versionId) {
        if (!ApplicationAccess.isRestricted(ApplicationAccess.authenticatedUser())) {
            return;
        }
        versions.findById(versionId).ifPresent(v -> ApplicationAccess.ensureCurrentUserCanAccess(v.getApplication()));
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
