package com.smartdeploy.alert;

import com.smartdeploy.application.Application;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.environment.Environment;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alerts",
        indexes = {
                @Index(
                        name = "idx_alert_application_environment_status",
                        columnList = "application_id, environment_id, status"
                ),
                @Index(
                        name = "idx_alert_type_status",
                        columnList = "type, status"
                )
        }
)
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id")
    private Deployment deployment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(length = 100)
    private String metricName;

    private Double currentValue;

    private Double thresholdValue;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastDetectedAt;

    private LocalDateTime resolvedAt;

    protected Alert() {
    }

    public Alert(
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
        this.application = application;
        this.environment = environment;
        this.deployment = deployment;
        this.type = type;
        this.severity = severity;
        this.status = AlertStatus.OPEN;
        this.title = title;
        this.message = message;
        this.metricName = metricName;
        this.currentValue = currentValue;
        this.thresholdValue = thresholdValue;

        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.lastDetectedAt = now;
    }

    public void markDetected(
            Deployment deployment,
            AlertSeverity severity,
            String title,
            String message,
            Double currentValue,
            Double thresholdValue
    ) {
        this.deployment = deployment;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.currentValue = currentValue;
        this.thresholdValue = thresholdValue;
        this.lastDetectedAt = LocalDateTime.now();
    }
    public void resolve() {
        this.status = AlertStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Application getApplication() {
        return application;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public AlertType getType() {
        return type;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getMetricName() {
        return metricName;
    }

    public Double getCurrentValue() {
        return currentValue;
    }

    public Double getThresholdValue() {
        return thresholdValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastDetectedAt() {
        return lastDetectedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
}