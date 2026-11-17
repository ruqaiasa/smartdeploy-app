package com.smartdeploy.auth.reset;

import com.smartdeploy.audit.AuditEvent;
import com.smartdeploy.auth.RefreshTokenService;
import com.smartdeploy.mail.MailService;
import com.smartdeploy.user.User;
import com.smartdeploy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Les quatre règles de sécurité du parcours « mot de passe oublié ». */
class PasswordResetServiceTest {

    private UserRepository users;
    private PasswordResetTokenRepository tokens;
    private RefreshTokenService refreshTokens;
    private MailService mail;
    private ApplicationEventPublisher events;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(PasswordResetTokenRepository.class);
        refreshTokens = mock(RefreshTokenService.class);
        mail = mock(MailService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new PasswordResetService(users, tokens, refreshTokens, encoder, mail, events, "http://localhost:5173/", 30);
        user = new User();
        user.setId(3L);
        user.setEmail("ruqaia@smartdeploy.com");
        user.setPassword(encoder.encode("ancienMotDePasse1"));
        user.setEnabled(true);
        user.setTokenVersion(4);
    }

    @Test
    void unknownAddressGetsNoEmailAndNoTrace() {
        when(users.findByEmailIgnoreCaseAndDeletedFalse("inconnu@exemple.com")).thenReturn(Optional.empty());

        service.request("  Inconnu@Exemple.com ");

        verifyNoInteractions(mail, tokens, events);
    }

    @Test
    void requestStoresOnlyTheHashAndSendsAOneTimeLink() {
        when(users.findByEmailIgnoreCaseAndDeletedFalse("ruqaia@smartdeploy.com")).thenReturn(Optional.of(user));
        PasswordResetToken previous = PasswordResetToken.builder().user(user).build();
        when(tokens.findByUserAndUsedAtIsNull(user)).thenReturn(List.of(previous));

        service.request("ruqaia@smartdeploy.com");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("ruqaia@smartdeploy.com"), contains("réinitialisation"), body.capture());
        Matcher link = Pattern.compile("http://localhost:5173/reset-password\\?token=([\\w-]+)").matcher(body.getValue());
        assertTrue(link.find(), "le lien pointe vers le frontend, sans double barre oblique");
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        assertNotEquals(link.group(1), saved.getValue().getTokenHash(), "jamais le jeton en clair dans la base");
        assertEquals(PasswordResetService.hash(link.group(1)), saved.getValue().getTokenHash());
        assertTrue(saved.getValue().getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(29)));
        assertNotNull(previous.getUsedAt(), "la demande précédente est invalidée");
        verify(events).publishEvent(any(AuditEvent.class));
    }

    @Test
    void requestsAreLimitedToThreePerHour() {
        when(users.findByEmailIgnoreCaseAndDeletedFalse("ruqaia@smartdeploy.com")).thenReturn(Optional.of(user));
        when(tokens.countByUserAndCreatedAtAfter(eq(user), any())).thenReturn(3L);

        service.request("ruqaia@smartdeploy.com");

        verify(tokens, never()).save(any());
        verifyNoInteractions(mail);
    }

    @Test
    void resetChangesThePasswordAndRevokesEverySession() {
        String token = "jeton-de-test";
        PasswordResetToken reset = PasswordResetToken.builder().user(user)
                .tokenHash(PasswordResetService.hash(token)).expiresAt(LocalDateTime.now().plusMinutes(10)).build();
        when(tokens.findByTokenHash(PasswordResetService.hash(token))).thenReturn(Optional.of(reset));

        service.reset(token, "nouveauMotDePasse2");

        assertTrue(encoder.matches("nouveauMotDePasse2", user.getPassword()));
        assertEquals(5, user.getTokenVersion(), "tous les jetons d'accès existants sont révoqués");
        verify(refreshTokens).revokeAll(user);
        assertNotNull(reset.getUsedAt(), "usage unique");
        verify(mail).send(eq("ruqaia@smartdeploy.com"), contains("modifié"), anyString());
    }

    @Test
    void expiredUsedOrWeakAreRefused() {
        PasswordResetToken expired = PasswordResetToken.builder().user(user)
                .tokenHash(PasswordResetService.hash("expire")).expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(tokens.findByTokenHash(PasswordResetService.hash("expire"))).thenReturn(Optional.of(expired));
        assertThrows(PasswordResetException.class, () -> service.reset("expire", "nouveauMotDePasse2"));
        assertFalse(service.isValid("expire"));

        PasswordResetToken used = PasswordResetToken.builder().user(user).usedAt(LocalDateTime.now())
                .tokenHash(PasswordResetService.hash("utilise")).expiresAt(LocalDateTime.now().plusMinutes(10)).build();
        when(tokens.findByTokenHash(PasswordResetService.hash("utilise"))).thenReturn(Optional.of(used));
        assertThrows(PasswordResetException.class, () -> service.reset("utilise", "nouveauMotDePasse2"));

        assertThrows(PasswordResetException.class, () -> PasswordResetService.checkStrength("courte1"));
        assertThrows(PasswordResetException.class, () -> PasswordResetService.checkStrength("sanschiffre"));
        assertDoesNotThrow(() -> PasswordResetService.checkStrength("motdepasse9"));
    }
}
