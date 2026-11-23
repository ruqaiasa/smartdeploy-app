package com.smartdeploy.user;

import com.smartdeploy.mail.AccountMailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * RG-U3 : l'adresse d'un nouvel inscrit est confirmée par un lien envoyé à cette adresse (24 h, usage unique).
 * Un administrateur ne valide qu'un compte dont l'adresse est confirmée.
 */
@Service
public class EmailVerificationService {

    static final int VALIDITY_HOURS = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationTokenRepository tokens;
    private final UserRepository users;
    private final AccountMailService mail;
    private final String frontendUrl;

    public EmailVerificationService(EmailVerificationTokenRepository tokens, UserRepository users, AccountMailService mail,
                                    @Value("${smartdeploy.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.tokens = tokens;
        this.users = users;
        this.mail = mail;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /** Envoie un nouveau lien de confirmation (les précédents restent valables jusqu'à leur expiration). */
    @Transactional
    public void issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        EmailVerificationToken t = new EmailVerificationToken();
        t.setUser(user);
        t.setTokenHash(hash(token));
        t.setCreatedAt(LocalDateTime.now());
        t.setExpiresAt(LocalDateTime.now().plusHours(VALIDITY_HOURS));
        tokens.save(t);
        mail.send(user.getEmail(), "SmartDeploy : confirmez votre adresse",
                "Bonjour " + user.getFirstName() + ",\n\nConfirmez votre adresse en ouvrant ce lien (valable 24 heures) :\n"
                        + frontendUrl + "/verify-email?token=" + token
                        + "\n\nUn administrateur validera ensuite votre compte.\n\nSi vous n'avez pas demandé de compte, ignorez ce message.\n");
    }

    /** Confirme l'adresse ; renvoie l'utilisateur concerné. */
    @Transactional
    public User verify(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Lien de confirmation invalide");
        }
        EmailVerificationToken t = tokens.findByTokenHash(hash(token.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Lien de confirmation invalide"));
        if (t.getUsedAt() != null) {
            throw new IllegalStateException("Ce lien a déjà été utilisé");
        }
        if (t.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Ce lien a expiré : demandez-en un nouveau");
        }
        t.setUsedAt(LocalDateTime.now());
        User user = t.getUser();
        user.setEmailVerified(true);
        return users.saveAndFlush(user);
    }

    /** Renvoie un lien, sans jamais révéler si l'adresse existe. */
    @Transactional
    public void resend(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        users.findByEmailIgnoreCaseAndDeletedFalse(email.trim())
                .filter(u -> !u.isEmailVerified() && u.isPendingApproval())
                .ifPresent(this::issue);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
