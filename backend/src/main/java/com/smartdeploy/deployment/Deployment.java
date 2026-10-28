package com.smartdeploy.deployment;

import com.smartdeploy.environment.Environment;
import com.smartdeploy.user.User;
import com.smartdeploy.version.Version;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "deployments",
        indexes = {
                @Index(
                        name = "idx_deployments_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_deployments_environment",
                        columnList = "environment_id"
                ),
                @Index(
                        name = "idx_deployments_version",
                        columnList = "version_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "version_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_deployment_version"
            )
    )
    private Version version;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "environment_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_deployment_environment"
            )
    )
    private Environment environment;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "requested_by",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_deployment_requested_by"
            )
    )
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private DeploymentStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "jenkins_build_number")
    private Integer jenkinsBuildNumber;

    @Column(
            name = "deployment_logs_url",
            length = 1000
    )
    private String deploymentLogsUrl;

    @Column(
            name = "kubernetes_deployment_name",
            length = 255
    )
    private String kubernetesDeploymentName;

    @Column(
            name = "error_message",
            length = 2000
    )
    private String errorMessage;

    /*
     * Si ce déploiement est un rollback, ce champ référence
     * le déploiement d'origine.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "rollback_of_id",
            foreignKey = @ForeignKey(
                    name = "fk_deployment_rollback_of"
            )
    )
    private Deployment rollbackOf;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = DeploymentStatus.PENDING;
        }
    }


    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}