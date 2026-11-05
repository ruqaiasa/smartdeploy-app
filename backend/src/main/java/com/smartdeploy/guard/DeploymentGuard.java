package com.smartdeploy.guard;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Surveillance d'un déploiement par le gardien, pendant la fenêtre qui suit
 * sa mise en service. Les contrôles de la dernière évaluation sont conservés
 * (en JSON) : ce sont les preuves de la décision.
 */
@Entity
@Table(
        name = "deployment_guards",
        indexes = {
                @Index(name = "idx_guards_deployment", columnList = "deployment_id"),
                @Index(name = "idx_guards_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeploymentGuard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(name = "environment_id", nullable = false)
    private Long environmentId;

    @Column(nullable = false, length = 255)
    private String namespace;

    @Column(name = "application_prefix", nullable = false, length = 255)
    private String applicationPrefix;

    @Convert(converter = GuardStatusConverter.class)
    @Column(nullable = false, length = 30)
    private GuardStatus status;

    @Column(length = 1000)
    private String verdict;

    @Column(name = "checks_json", columnDefinition = "text")
    private String checksJson;

    @Column(nullable = false)
    private int evaluations;

    @Column(name = "evaluations_with_data", nullable = false)
    private int evaluationsWithData;

    @Column(name = "consecutive_critical", nullable = false)
    private int consecutiveCritical;

    /** Avertissements retenus (persistants), avec leurs mesures : la raison des réserves. */
    @Column(name = "retained_warnings", length = 1000)
    private String retainedWarnings;

    /** Avertissements consécutifs : seuls les avertissements persistants comptent. */
    @Column(name = "consecutive_warnings", nullable = false, columnDefinition = "integer default 0")
    private int consecutiveWarnings;

    @Column(name = "warnings_seen", nullable = false)
    private boolean warningsSeen;

    /** Déploiement vers lequel un retour arrière est proposé (si dégradé). */
    @Column(name = "rollback_target_id")
    private Long rollbackTargetId;

    /** Retour arrière lancé automatiquement par le gardien, le cas échéant. */
    @Column(name = "auto_rollback_deployment_id")
    private Long autoRollbackDeploymentId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "last_evaluated_at")
    private LocalDateTime lastEvaluatedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;
}
