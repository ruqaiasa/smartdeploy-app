package com.smartdeploy.deployment;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "deployment_kubernetes_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeploymentKubernetesConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "deployment_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(
                    name = "fk_kubernetes_config_deployment"
            )
    )
    private Deployment deployment;

    @Column(nullable = false, length = 255)
    private String namespace;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_type", nullable = false, length = 30)
    private KubernetesApplicationType applicationType;

    @Column(name = "configuration_json", nullable = false, columnDefinition = "TEXT")
    private String configurationJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}