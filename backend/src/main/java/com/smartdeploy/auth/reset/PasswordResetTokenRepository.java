package com.smartdeploy.auth.reset;

import com.smartdeploy.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Demandes récentes d'un compte : limite de fréquence. */
    long countByUserAndCreatedAtAfter(User user, LocalDateTime after);

    /** Demandes encore utilisables : une nouvelle demande les invalide. */
    List<PasswordResetToken> findByUserAndUsedAtIsNull(User user);
}
