package com.smartdeploy.canary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Un déploiement progressif : un pod de la nouvelle version, à côté de la version stable, sous observation. */
@Entity
@Table(name = "canary_releases", indexes = @Index(name = "idx_canary_deployment", columnList = "deployment_id", unique = true))
@Getter
@Setter
@NoArgsConstructor
public class CanaryRelease {

    public enum State { OBSERVING, PROMOTED, ABORTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(name = "environment_id", nullable = false)
    private Long environmentId;

    @Column(nullable = false, length = 120)
    private String namespace;

    /** Noms des composants (serveur, interface), séparés par des virgules. */
    @Column(nullable = false, length = 400)
    private String components;

    /** Nombre de pods de la version stable, pour estimer la part du trafic reçue par le canary. */
    @Column(name = "stable_replicas", nullable = false)
    private int stableReplicas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;
}
