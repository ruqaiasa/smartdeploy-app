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

    private com.smartdeploy.application.ApplicationAccess access;

    /** RG-O1 : contrôle d'accès aux applications (facultatif, absent des tests unitaires). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAccess(com.smartdeploy.application.ApplicationAccess access) {
        this.access = access;
    }

    /** RG-O1 : un développeur ne voit que les alertes de ses applications. */
    private boolean visible(Alert alert) {
        Long ownerId = access == null ? null : access.ownerFilter();
        if (ownerId == null) {
            return true;
        }
        Application application = alert.getApplication();
        return application != null && application.getOwner() != null && ownerId.equals(application.getOwner().getId());
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
        if (access != null) {
            access.ensureCanAccess(application);
        }

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
        if (access != null) {
            access.ensureCanAccess(application);
        }

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
    @Transactional(readOnly = true)
    public List<AlertResponse> findAll() {

        return alertRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(this::visible)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> findOpen() {

        return alertRepository
                .findByStatusOrderByCreatedAtDesc(
                        AlertStatus.OPEN
                )
                .stream()
                .filter(this::visible)
                .map(this::toResponse)
                .toList();
    }
}