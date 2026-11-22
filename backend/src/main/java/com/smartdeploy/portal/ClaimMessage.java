package com.smartdeploy.portal;

import com.smartdeploy.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Un élément du fil d'une réclamation : un message, ou un événement (changement d'état). */
@Entity
@Table(name = "claim_messages", indexes = @Index(name = "idx_claim_message_claim", columnList = "claim_id"))
@Getter
@Setter
@NoArgsConstructor
public class ClaimMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, length = 2000)
    private String message;

    /** Événement de la plateforme (changement d'état), plutôt qu'un message d'une personne. */
    @Column(nullable = false)
    private boolean systemEvent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
