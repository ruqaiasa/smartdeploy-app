package com.smartdeploy.auth.reset;

import com.smartdeploy.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Demande de réinitialisation du mot de passe. Seule l'empreinte SHA-256 du jeton est
 * conservée : une fuite de la base ne permet pas de réinitialiser un compte.
 */
@Entity
@Table(name = "password_reset_tokens", indexes = @Index(name = "idx_password_reset_hash", columnList = "token_hash", unique = true))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_password_reset_user"))
    private User user;

    /** Empreinte SHA-256 du jeton (hexadécimal), jamais le jeton lui-même. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Renseigné à l'usage, ou à l'invalidation par une demande plus récente : usage unique. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
