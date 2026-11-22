package com.smartdeploy.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Retour d'un DevOps sur une analyse : juste, partielle ou fausse (mesure de la précision de l'analyste). */
@Entity
@Table(name = "analysis_feedback")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisFeedback {

    public enum Verdict { CORRECT, PARTIAL, WRONG }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(length = 60)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Verdict verdict;

    @Column(name = "actual_cause", length = 1000)
    private String actualCause;

    @Column(name = "author_email", length = 200)
    private String authorEmail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
