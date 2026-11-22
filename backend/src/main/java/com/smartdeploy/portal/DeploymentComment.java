package com.smartdeploy.portal;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Un message de la discussion attachée à un déploiement (portail développeur). */
@Entity
@Table(name = "deployment_comments", indexes = @Index(name = "idx_comment_deployment", columnList = "deployment_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
