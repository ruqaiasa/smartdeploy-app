package com.smartdeploy.incident;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Diagnostic enregistré d'un incident. Les textes (code, confiance,
 * déclencheur) sont stockés en clair, sans énumération figée en base.
 */
@Entity
@Table(name = "incident_analyses", indexes = @Index(name = "idx_incident_deployment", columnList = "deployment_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(name = "trigger_source", nullable = false, length = 20)
    private String trigger;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 10)
    private String confidence;

    @Column(length = 1000)
    private String explanation;

    @Column(length = 1000)
    private String action;

    @Column(name = "evidence_json", columnDefinition = "text")
    private String evidenceJson;

    @Column(name = "alternatives_json", columnDefinition = "text")
    private String alternativesJson;

    @Column(name = "notes_json", columnDefinition = "text")
    private String notesJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
