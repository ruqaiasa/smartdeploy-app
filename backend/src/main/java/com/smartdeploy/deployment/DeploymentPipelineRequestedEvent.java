package com.smartdeploy.deployment;

/**
 * Publié par DeploymentService lorsqu'un déploiement PENDING doit être
 * transmis à Jenkins. Traité uniquement APRÈS le commit de la transaction
 * (voir DeploymentPipelineLauncher), afin que Jenkins ne puisse jamais
 * rappeler SmartDeploy pour un déploiement encore invisible en base.
 */
public record DeploymentPipelineRequestedEvent(Long deploymentId) {
}
