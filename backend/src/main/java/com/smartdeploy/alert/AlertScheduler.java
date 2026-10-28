package com.smartdeploy.alert;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.monitoring.MonitoringService;
import com.smartdeploy.monitoring.dto.ApplicationMetricsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AlertScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AlertScheduler.class);

    private final DeploymentRepository deploymentRepository;
    private final MonitoringService monitoringService;
    private final AlertDetectionService alertDetectionService;

    public AlertScheduler(
            DeploymentRepository deploymentRepository,
            MonitoringService monitoringService,
            AlertDetectionService alertDetectionService
    ) {
        this.deploymentRepository = deploymentRepository;
        this.monitoringService = monitoringService;
        this.alertDetectionService = alertDetectionService;
    }

    @Scheduled(fixedDelayString = "${alerts.scheduler.delay-ms:30000}")
    public void evaluateDeployments() {

        LOGGER.debug(
                "Début du cycle de surveillance des alertes"
        );

        List<Deployment> successfulDeployments =
                deploymentRepository
                        .findByStatusAndDeletedFalseAndKubernetesDeploymentNameIsNotNullOrderByFinishedAtDesc(
                                DeploymentStatus.SUCCESS
                        );

        List<Deployment> deploymentsToMonitor =
                successfulDeployments.stream()
                        .filter(deployment ->
                                deployment.getEnvironment() != null
                        )
                        .filter(deployment ->
                                deployment.getEnvironment().getId() != null
                        )
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        deployment ->
                                                deployment.getEnvironment().getId(),

                                        deployment ->
                                                deployment,

                                        (mostRecent, older) ->
                                                mostRecent,

                                        java.util.LinkedHashMap::new
                                )
                        )
                        .values()
                        .stream()
                        .toList();

        LOGGER.debug(
                "{} déploiement(s) actif(s) à surveiller "
                        + "sur {} déploiement(s) réussi(s)",
                deploymentsToMonitor.size(),
                successfulDeployments.size()
        );

        for (Deployment deployment : deploymentsToMonitor) {

            evaluateDeployment(deployment);
        }
    }
    private void evaluateDeployment(
            Deployment deployment
    ) {

        try {

            LOGGER.debug(
                    "Analyse du déploiement #{} ({})",
                    deployment.getId(),
                    deployment.getKubernetesDeploymentName()
            );

            ApplicationMetricsResponse metrics =
                    monitoringService.getDeploymentMetrics(
                            deployment.getId()
                    );

            LOGGER.debug(
                    "Métriques du déploiement #{} : "
                            + "runningPods={}, desiredPods={}, restartCount={}",
                    deployment.getId(),
                    metrics.runningPods(),
                    metrics.desiredPods(),
                    metrics.restartCount()
            );

            alertDetectionService.evaluateApplicationHealth(
                    deployment.getEnvironment().getApplication(),
                    deployment.getEnvironment(),
                    deployment,
                    metrics.runningPods(),
                    metrics.desiredPods(),
                    metrics.restartCount()
            );

            LOGGER.debug(
                    "Évaluation terminée pour le déploiement #{}",
                    deployment.getId()
            );

        } catch (Exception exception) {

            LOGGER.error(
                    "Impossible d'évaluer les alertes du déploiement #{} ({})",
                    deployment.getId(),
                    deployment.getKubernetesDeploymentName(),
                    exception
            );
        }
    }
}