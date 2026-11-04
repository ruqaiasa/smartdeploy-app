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

    /** Facultatif : trace l'ouverture et la résolution des alertes dans le journal. */
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setEventPublisher(org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    private void publishAlertEvent(com.smartdeploy.audit.AuditAction action, Alert alert,
                                   Application application, Environment environment, String summary) {
        if (eventPublisher == null) {
            return;
        }
        eventPublisher.publishEvent(com.smartdeploy.audit.AuditEvent.of(
                action, "Système (supervision)", "ALERT", alert.getId(),
                application == null ? null : application.getName(),
                environment == null ? null : environment.getName(),
                summary
        ));
    }

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
                    "Au moins " + CRITICAL_RESTART_THRESHOLD + " redémarrages de conteneurs (application ou base de données) au cours des 15 dernières minutes.",
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
                    "Plusieurs redémarrages de conteneurs (application ou base de données) au cours des 15 dernières minutes.",
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

        Alert saved = alertRepository.save(alert);

        publishAlertEvent(com.smartdeploy.audit.AuditAction.ALERT_OPENED, saved, application, environment,
                "[" + severity + "] " + title + " : " + message
                        + (currentValue == null ? "" : " (valeur " + formatValue(currentValue)
                        + (thresholdValue == null ? "" : ", seuil " + formatValue(thresholdValue)) + ")"));
    }

    private static String formatValue(Double value) {
        return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.format("%.2f", value);
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
                    Alert saved = alertRepository.save(alert);
                    publishAlertEvent(com.smartdeploy.audit.AuditAction.ALERT_RESOLVED, saved,
                            application, environment, saved.getTitle() + " : retour à la normale");
                });
    }
}