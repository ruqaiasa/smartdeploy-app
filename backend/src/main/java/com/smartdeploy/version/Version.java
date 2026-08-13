package com.smartdeploy.version;

import com.smartdeploy.application.Application;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "versions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_versions_application_version",
                        columnNames = {
                                "application_id",
                                "version"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Version {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            nullable = false,
            length = 50
    )
    private String version;

    @Column(
            name = "git_branch",
            nullable = false,
            length = 150
    )
    private String gitBranch;

    @Column(
            name = "git_commit",
            length = 100
    )
    private String gitCommit;

    @Column(
            name = "docker_image",
            length = 500
    )
    private String dockerImage;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "build_status",
            nullable = false,
            length = 30
    )
    private BuildStatus buildStatus;

    @Column(name = "build_number")
    private Integer buildNumber;

    @Column(
            name = "artifact_path",
            length = 500
    )
    private String artifactPath;

    @Column(
            name = "build_logs_url",
            length = 1000
    )
    private String buildLogsUrl;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "application_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_version_application"
            )
    )
    private Application application;

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

        if (buildStatus == null) {
            buildStatus = BuildStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}