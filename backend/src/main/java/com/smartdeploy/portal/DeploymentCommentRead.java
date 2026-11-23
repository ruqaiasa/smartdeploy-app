package com.smartdeploy.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Dernière lecture d'une discussion par une personne : les messages plus récents sont « non lus ». */
@Entity
@Table(name = "deployment_comment_reads",
        uniqueConstraints = @UniqueConstraint(name = "uk_comment_read_user_deployment", columnNames = {"user_id", "deployment_id"}))
@Getter
@Setter
@NoArgsConstructor
public class DeploymentCommentRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(name = "last_read_at", nullable = false)
    private LocalDateTime lastReadAt;
}
