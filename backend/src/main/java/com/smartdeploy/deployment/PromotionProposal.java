package com.smartdeploy.deployment;

import com.smartdeploy.integration.kubernetes.KubernetesApplicationRequest;

/**
 * Proposition de promotion (RG-P3, RG-P4) : un déploiement validé par le gardien peut monter
 * vers l'environnement suivant de son application, avec la même version et la même configuration.
 */
public record PromotionProposal(
        Long sourceDeploymentId,
        String sourceEnvironmentName,
        Long applicationId,
        Long versionId,
        String version,
        Long targetEnvironmentId,
        String targetEnvironmentName,
        String targetEnvironmentType,
        KubernetesApplicationRequest configuration
) {
}
