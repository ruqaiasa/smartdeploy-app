package com.smartdeploy.alert;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.ApplicationNotFoundException;
import com.smartdeploy.application.ApplicationRepository;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentNotFoundException;
import com.smartdeploy.environment.EnvironmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final ApplicationRepository applicationRepository;
    private final EnvironmentRepository environmentRepository;

    public AlertService(
            AlertRepository alertRepository,
            ApplicationRepository applicationRepository,
            EnvironmentRepository environmentRepository
    ) {
        this.alertRepository = alertRepository;
        this.applicationRepository = applicationRepository;
        this.environmentRepository = environmentRepository;
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> findAll(
            Long applicationId,
            Long environmentId
    ) {

        Application application = applicationRepository
                .findByIdAndDeletedFalse(applicationId)
                .orElseThrow(
                        () -> new ApplicationNotFoundException(applicationId)
                );

        Environment environment = environmentRepository
                .findByIdAndDeletedFalse(environmentId)
                .orElseThrow(
                        () -> new EnvironmentNotFoundException(environmentId)
                );

        validateEnvironmentApplication(
                application,
                environment
        );

        return alertRepository
                .findByApplicationAndEnvironmentOrderByCreatedAtDesc(
                        application,
                        environment
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> findOpen(
            Long applicationId,
            Long environmentId
    ) {

        Application application = applicationRepository
                .findByIdAndDeletedFalse(applicationId)
                .orElseThrow(
                        () -> new ApplicationNotFoundException(applicationId)
                );

        Environment environment = environmentRepository
                .findByIdAndDeletedFalse(environmentId)
                .orElseThrow(
                        () -> new EnvironmentNotFoundException(environmentId)
                );

        validateEnvironmentApplication(
                application,
                environment
        );

        return alertRepository
                .findByApplicationAndEnvironmentAndStatusOrderByCreatedAtDesc(
                        application,
                        environment,
                        AlertStatus.OPEN
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateEnvironmentApplication(
            Application application,
            Environment environment
    ) {
        if (!environment.getApplication().getId()
                .equals(application.getId())) {

            throw new IllegalArgumentException(
                    "L'environnement n'appartient pas à l'application demandée."
            );
        }
    }

    private AlertResponse toResponse(Alert alert) {

        Long deploymentId =
                alert.getDeployment() != null
                        ? alert.getDeployment().getId()
                        : null;

        return new AlertResponse(
                alert.getId(),
                alert.getApplication().getId(),
                alert.getApplication().getName(),
                alert.getEnvironment().getId(),
                alert.getEnvironment().getName(),
                deploymentId,
                alert.getType(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getTitle(),
                alert.getMessage(),
                alert.getMetricName(),
                alert.getCurrentValue(),
                alert.getThresholdValue(),
                alert.getCreatedAt(),
                alert.getLastDetectedAt(),
                alert.getResolvedAt()
        );
    }
}