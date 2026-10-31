package com.smartdeploy.deployment;

import com.smartdeploy.integration.jenkins.JenkinsCancellationResult;
import com.smartdeploy.integration.jenkins.JenkinsService;
import com.smartdeploy.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Déclenche le pipeline Jenkins une fois le déploiement validé et commité.
 *
 * Si Jenkins refuse ou est injoignable, le déploiement passe à FAILED avec
 * un message exploitable au lieu de disparaître dans un rollback : l'échec
 * reste ainsi traçable dans SmartDeploy et l'environnement n'est pas bloqué.
 */
@Component
public class DeploymentPipelineLauncher {

    private static final Logger log =
            LoggerFactory.getLogger(DeploymentPipelineLauncher.class);

    private static final int MAX_ERROR_LENGTH = 2000;

    private final DeploymentRepository deploymentRepository;
    private final JenkinsService jenkinsService;
    private final DeploymentService deploymentService;

    public DeploymentPipelineLauncher(
            DeploymentRepository deploymentRepository,
            JenkinsService jenkinsService,
            DeploymentService deploymentService
    ) {
        this.deploymentRepository = deploymentRepository;
        this.jenkinsService = jenkinsService;
        this.deploymentService = deploymentService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPipelineRequested(DeploymentPipelineRequestedEvent event) {

        Deployment deployment = deploymentRepository
                .findActiveByIdForUpdate(event.deploymentId())
                .orElse(null);

        if (deployment == null
                || deployment.getStatus() != DeploymentStatus.PENDING) {
            return;
        }

        try {
            String queueUrl =
                    jenkinsService.triggerDeployment(deployment).queueUrl();

            deployment.setJenkinsQueueUrl(queueUrl);
            deploymentRepository.saveAndFlush(deployment);

            log.info(
                    "Pipeline Jenkins mis en file pour le déploiement {} : {}",
                    deployment.getId(),
                    queueUrl
            );
        } catch (RuntimeException exception) {

            log.error(
                    "Déclenchement Jenkins impossible pour le déploiement {}",
                    deployment.getId(),
                    exception
            );

            LocalDateTime now = LocalDateTime.now();
            deployment.setStatus(DeploymentStatus.FAILED);
            deployment.setFinishedAt(now);
            deployment.setDurationSeconds(
                    deployment.getStartedAt() == null
                            ? null
                            : Math.max(
                            Duration.between(deployment.getStartedAt(), now)
                                    .getSeconds(),
                            0
                    )
            );
            deployment.setErrorMessage(truncate(
                    "Déclenchement du pipeline Jenkins impossible : "
                            + (exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage())
            ));
            deploymentRepository.saveAndFlush(deployment);
        }
    }

    /**
     * Annulation demandée par un utilisateur.
     *
     * Volontairement non transactionnel : les appels à Jenkins (réseau)
     * ne doivent pas tenir de verrou en base. Le statut est contrôlé
     * avant l'appel, puis revérifié sous verrou par markCancelled.
     */
    public DeploymentResponse cancel(Long deploymentId, User currentUser) {
        Deployment deployment = deploymentRepository
                .findByIdAndDeletedFalse(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));

        deploymentService.ensureCancellable(deployment);

        JenkinsCancellationResult result = jenkinsService.cancelPipeline(
                deployment.getJenkinsQueueUrl(),
                deployment.getJenkinsBuildNumber()
        );

        if (!result.jenkinsReached()) {
            log.warn("Annulation du déploiement {} : {}", deploymentId, result.message());
        }

        return deploymentService.markCancelled(deploymentId, currentUser, result.message());
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_LENGTH);
    }

}
