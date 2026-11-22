package com.smartdeploy.portal;

import com.smartdeploy.application.Application;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Une réclamation d'un développeur : un incident, un échec contesté, un besoin, une question. */
@Entity
@Table(name = "claims")
@Getter
@Setter
@NoArgsConstructor
public class Claim {

    public enum Type { INCIDENT, CONTESTED_FAILURE, RESOURCES, ACCESS, QUESTION }

    public enum Priority { CRITICAL, HIGH, NORMAL }

    public enum Status { OPEN, IN_PROGRESS, WAITING_REQUESTER, RESOLVED, CLOSED }

    public enum Cause { CODE, CONFIGURATION, RESOURCES, PLATFORM, DEPENDENCY, USAGE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private Environment environment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id")
    private Deployment deployment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /** Contexte joint automatiquement à l'ouverture. */
    @Column(length = 4000)
    private String context;

    @Column(length = 4000)
    private String resolution;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Cause cause;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
