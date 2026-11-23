package com.smartdeploy.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Historique de la note d'excellence d'une application, pour montrer sa progression. */
@Entity
@Table(name = "scorecard_snapshots", indexes = @Index(name = "idx_scorecard_application", columnList = "application_id"))
@Getter
@Setter
@NoArgsConstructor
public class ScorecardSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, length = 1)
    private String grade;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
