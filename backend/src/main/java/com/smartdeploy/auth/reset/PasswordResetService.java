package com.smartdeploy.auth.reset;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import com.smartdeploy.auth.RefreshTokenService;
import com.smartdeploy.mail.MailService;
import com.smartdeploy.user.User;
import com.smartdeploy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Mot de passe oublié, en quatre règles de sécurité :
 * 1. la réponse est identique que le compte existe ou non (aucune énumération des adresses) ;
 * 2. le jeton est aléatoire (256 bits), à usage unique, valable 30 minutes, et seule son empreinte est stockée ;
 * 3. au plus 3 demandes par heure et par compte ;
 * 4. le changement révoque toutes les sessions et le compte est prévenu par e-mail.
 */
@Service
@Transactional
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    static final int MAX_REQUESTS_PER_HOUR = 3;
    static final String ACTOR = "Réinitialisation du mot de passe";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    /** Courriels destinés à l'utilisateur : vrai serveur SMTP s'il est configuré (sinon, serveur interne). */
    private com.smartdeploy.mail.AccountMailService accountMail;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAccountMail(com.smartdeploy.mail.AccountMailService accountMail) {
        this.accountMail = accountMail;
    }

    private void userMail(String to, String subject, String text) {
        if (accountMail != null) {
            accountMail.send(to, subject, text);
        } else {
            mailService.send(to, subject, text);
        }
    }
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendUrl;
    private final long ttlMinutes;

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                RefreshTokenService refreshTokenService, PasswordEncoder passwordEncoder,
                                MailService mailService, ApplicationEventPublisher eventPublisher,
                                @Value("${smartdeploy.frontend-url:http://localhost:5173}") String frontendUrl,
                                @Value("${smartdeploy.password-reset.ttl-minutes:30}") long ttlMinutes) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.eventPublisher = eventPublisher;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.ttlMinutes = ttlMinutes;
    }

    /** Demande de réinitialisation : ne renvoie rien, pour ne jamais révéler si le compte existe. */
    public void request(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        Optional<User> found = userRepository.findByEmailIgnoreCaseAndDeletedFalse(normalized)
                .filter(User::isEnabled);
        if (found.isEmpty()) {
            log.info("Mot de passe oublié : demande pour une adresse inconnue ou inactive (aucun e-mail envoyé)");
            return;
        }
        User user = found.get();
        LocalDateTime now = LocalDateTime.now();
        if (tokenRepository.countByUserAndCreatedAtAfter(user, now.minusHours(1)) >= MAX_REQUESTS_PER_HOUR) {
            log.warn("Mot de passe oublié : limite de {} demandes par heure atteinte pour l'utilisateur #{}", MAX_REQUESTS_PER_HOUR, user.getId());
            return;
        }
        // Une seule demande utilisable à la fois : les précédentes sont invalidées.
        tokenRepository.findByUserAndUsedAtIsNull(user).forEach(previous -> previous.setUsedAt(now));

        String token = newToken();
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash(token))
                .expiresAt(now.plusMinutes(ttlMinutes))
                .createdAt(now)
                .build());

        userMail(user.getEmail(), "SmartDeploy : réinitialisation de votre mot de passe",
                "Bonjour,\n\n"
                        + "Une réinitialisation du mot de passe de votre compte SmartDeploy a été demandée.\n"
                        + "Pour choisir un nouveau mot de passe, ouvrez ce lien (valable " + ttlMinutes + " minutes, utilisable une seule fois) :\n\n"
                        + frontendUrl + "/reset-password?token=" + token + "\n\n"
                        + "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message : votre mot de passe reste inchangé.\n");
        eventPublisher.publishEvent(new AuditEvent(AuditAction.PASSWORD_RESET_REQUESTED, user.getEmail(), "USER", user.getId(),
                null, null, "Réinitialisation du mot de passe demandée pour " + user.getEmail(),
                "Lien envoyé par e-mail, valable " + ttlMinutes + " minutes", false));
    }

    /** Le lien est-il encore utilisable ? (pour afficher le formulaire ou « lien expiré ») */
    @Transactional(readOnly = true)
    public boolean isValid(String token) {
        return usable(token).isPresent();
    }

    /** Choix du nouveau mot de passe. */
    public void reset(String token, String newPassword) {
        PasswordResetToken reset = usable(token)
                .orElseThrow(() -> new PasswordResetException("Ce lien est invalide, expiré ou déjà utilisé. Faites une nouvelle demande."));
        checkStrength(newPassword);
        User user = reset.getUser();
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new PasswordResetException("Le nouveau mot de passe doit être différent de l'ancien.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        // Toutes les sessions ouvertes, y compris celles d'un éventuel intrus, sont révoquées.
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokenService.revokeAll(user);
        reset.setUsedAt(LocalDateTime.now());

        userMail(user.getEmail(), "SmartDeploy : votre mot de passe a été modifié",
                "Bonjour,\n\n"
                        + "Le mot de passe de votre compte SmartDeploy vient d'être modifié, et toutes vos sessions ont été fermées.\n"
                        + "Si vous n'êtes pas à l'origine de ce changement, contactez immédiatement un administrateur.\n");
        eventPublisher.publishEvent(new AuditEvent(AuditAction.PASSWORD_RESET_COMPLETED, user.getEmail(), "USER", user.getId(),
                null, null, "Mot de passe réinitialisé pour " + user.getEmail(),
                "Toutes les sessions ont été révoquées", false));
    }

    /** Au moins 8 caractères, avec au moins une lettre et un chiffre. */
    static void checkStrength(String password) {
        if (password == null || password.length() < 8 || password.length() > 100
                || !password.matches(".*[A-Za-zÀ-ÿ].*") || !password.matches(".*\\d.*")) {
            throw new PasswordResetException("Le mot de passe doit contenir entre 8 et 100 caractères, dont au moins une lettre et un chiffre.");
        }
    }

    private Optional<PasswordResetToken> usable(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        return tokenRepository.findByTokenHash(hash(token.trim()))
                .filter(reset -> reset.getUsedAt() == null && reset.getExpiresAt().isAfter(now))
                .filter(reset -> reset.getUser().isEnabled() && !reset.getUser().isDeleted());
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }
}
