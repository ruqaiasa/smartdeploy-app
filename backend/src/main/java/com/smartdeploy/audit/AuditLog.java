package com.smartdeploy.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entrée du journal d'activité : qui a fait quoi, quand, et sur quoi.
 * Les entrées ne sont jamais modifiées.
 */
@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_audit_logs_action", columnList = "action")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(nullable = false, length = 255)
    private String actor;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "application_name", length = 255)
    private String applicationName;

    @Column(name = "environment_name", length = 255)
    private String environmentName;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;
}
