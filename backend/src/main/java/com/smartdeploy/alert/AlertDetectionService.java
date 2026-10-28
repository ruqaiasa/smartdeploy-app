package com.smartdeploy.alert;

import com.smartdeploy.application.Application;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.environment.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AlertDetectionService {

    private static final int WARNING_RESTART_THRESHOLD = 2;
    private static final int CRITICAL_RESTART_THRESHOLD = 5;

    private final AlertRepository alertRepository;

    public AlertDetectionService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Transactional
    public void evaluateApplicationHealth(
            Application application,
            Environment environment,
            Deployment deployment,
            int runningPods,
            int desiredPods,
            int restartCount
    ) {

        evaluatePodAvailability(
                application,
                environment,
                deployment,
                runningPods,
                desiredPods
        );

        evaluateRestartCount(
                application,
                environment,
                deployment,
                restartCount
        );
    }

    private void evaluatePodAvailability(
            Application application,
            Environment environment,
            Deployment deployment,
            int runningPods,
            int desiredPods
    ) {

        if (desiredPods <= 0) {

            resolveAlert(
                    application,
                    environment,
                    AlertType.POD_UNAVAILABLE
            );

            resolveAlert(
                    application,
                    environment,
                    AlertType.POD_AVAILABILITY_DEGRADED
            );

            return;
        }

        if (runningPods == 0) {

            resolveAlert(
                    application,
                    environment,
                    AlertType.POD_AVAILABILITY_DEGRADED
            );

            createOrUpdateAlert(
                    application,
                    environment,
                    deployment,
                    AlertType.POD_UNAVAILABLE,
                    AlertSeverity.CRITICAL,
                    "Application indisponible",
                    "Aucun pod n'est actuellement disponible.",
                    "runningPods",
                    (double) runningPods,
                    (double) desiredPods
            );

            return;
        }

        if (runningPods < desiredPods) {

            resolveAlert(
                    application,
                    environment,
                    AlertType.POD_UNAVAILABLE
            );

            createOrUpdateAlert(
                    application,
                    environment,
                    deployment,
                    AlertType.POD_AVAILABILITY_DEGRADED,
                    AlertSeverity.CRITICAL,
                    "Disponibilité des pods dégradée",
                    "Le nombre de pods disponibles est inférieur au nombre attendu.",
                    "runningPods",
                    (double) runningPods,
                    (double) desiredPods
            );

            return;
        }

        resolveAlert(
                application,
                environment,
                AlertType.POD_UNAVAILABLE
        );

        resolveAlert(
                application,
                environment,
                AlertType.POD_AVAILABILITY_DEGRADED
        );
    }
    private void evaluateRestartCount(
            Application application,
            Environment environment,
            Deployment deployment,
            int restartCount
    ) {

        if (restartCount >= CRITICAL_RESTART_THRESHOLD) {

            createOrUpdateAlert(
                    application,
                    environment,
                    deployment,
                    AlertType.HIGH_RESTART_COUNT,
                    AlertSeverity.CRITICAL,
                    "Nombre élevé de redémarrages",
                    "Le nombre de redémarrages des conteneurs a atteint un niveau critique.",
                    "restartCount",
                    (double) restartCount,
                    (double) CRITICAL_RESTART_THRESHOLD
            );

            return;
        }

        if (restartCount >= WARNING_RESTART_THRESHOLD) {

            createOrUpdateAlert(
                    application,
                    environment,
                    deployment,
                    AlertType.HIGH_RESTART_COUNT,
                    AlertSeverity.WARNING,
                    "Redémarrages répétés",
                    "Plusieurs redémarrages de conteneurs ont été détectés.",
                    "restartCount",
                    (double) restartCount,
                    (double) WARNING_RESTART_THRESHOLD
            );

            return;
        }

        resolveAlert(
                application,
                environment,
                AlertType.HIGH_RESTART_COUNT
        );
    }

    private void createOrUpdateAlert(
            Application application,
            Environment environment,
            Deployment deployment,
            AlertType type,
            AlertSeverity severity,
            String title,
            String message,
            String metricName,
            Double currentValue,
            Double thresholdValue
    ) {

        Optional<Alert> existingAlert =
                alertRepository
                        .findByApplicationAndEnvironmentAndTypeAndStatus(
                                application,
                                environment,
                                type,
                                AlertStatus.OPEN
                        );

        if (existingAlert.isPresent()) {

            Alert alert = existingAlert.get();

            alert.markDetected(
                    deployment,
                    severity,
                    title,
                    message,
                    currentValue,
                    thresholdValue
            );

            alertRepository.save(alert);

            return;
        }

        Alert alert = new Alert(
                application,
                environment,
                deployment,
                type,
                severity,
                title,
                message,
                metricName,
                currentValue,
                thresholdValue
        );

        alertRepository.save(alert);
    }

    private void resolveAlert(
            Application application,
            Environment environment,
            AlertType type
    ) {

        alertRepository
                .findByApplicationAndEnvironmentAndTypeAndStatus(
                        application,
                        environment,
                        type,
                        AlertStatus.OPEN
                )
                .ifPresent(alert -> {
                    alert.resolve();
                    alertRepository.save(alert);
                });
    }
}