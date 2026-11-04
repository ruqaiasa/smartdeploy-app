package com.smartdeploy.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entrée du journal d'activité : qui a fait quoi, quand, d'où, et sur quoi.
 *
 * Les entrées forment une chaîne : chacune contient l'empreinte de la
 * précédente et la sienne (voir AuditChain). Toute modification ou
 * suppression directe en base rompt la chaîne et devient détectable.
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

    @Convert(converter = AuditActionConverter.class)
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

    @Column(length = 2000)
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    /** Empreinte de l'entrée précédente (chaîne d'intégrité). */
    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    /** Empreinte SHA-256 de cette entrée, incluant previousHash. */
    @Column(length = 64)
    private String hash;
}
