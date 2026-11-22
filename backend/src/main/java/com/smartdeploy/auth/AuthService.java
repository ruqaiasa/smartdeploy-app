package com.smartdeploy.auth;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import com.smartdeploy.security.jwt.JwtService;
import com.smartdeploy.user.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final long ACCESS_TOKEN_EXPIRATION_SECONDS = 3600;

    /** Message transmis au gestionnaire d'erreurs pour un compte en attente de validation. */
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";

    private com.smartdeploy.user.UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setUserRepository(com.smartdeploy.user.UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            normalizedEmail,
                            request.password()
                    )
            );
        } catch (AuthenticationException exception) {
            // RG-U1 : un compte en attente de validation reçoit un message précis, pas « désactivé ».
            if (exception instanceof org.springframework.security.authentication.DisabledException && userRepository != null
                    && userRepository.findByEmailIgnoreCaseAndDeletedFalse(normalizedEmail)
                            .map(com.smartdeploy.user.User::isPendingApproval).orElse(false)) {
                throw new org.springframework.security.authentication.DisabledException(PENDING_APPROVAL);
            }
            // Tracé même si la transaction est annulée (keepOnRollback).
            eventPublisher.publishEvent(AuditEvent.securityFailure(
                    AuditAction.LOGIN_FAILURE,
                    normalizedEmail,
                    "Échec de connexion : identifiants invalides ou compte inactif"
            ));
            throw exception;
        }

        User user = (User) authentication.getPrincipal();

        eventPublisher.publishEvent(AuditEvent.of(
                AuditAction.LOGIN_SUCCESS, user.getEmail(), "USER", user.getId(),
                null, null, "Connexion réussie"
        ));

        String accessToken = generateAccessToken(user);

        RefreshToken refreshToken =
                refreshTokenService.create(user);

        return buildResponse(
                user,
                accessToken,
                refreshToken.getToken()
        );
    }

    @Transactional
    public AuthResponse refresh(
            RefreshTokenRequest request
    ) {
        RefreshToken oldRefreshToken =
                refreshTokenService.verify(request.refreshToken());

        User user = oldRefreshToken.getUser();

        /*
         * Rotation du refresh token :
         * l'ancien token devient inutilisable.
         */
        oldRefreshToken.setRevoked(true);

        RefreshToken newRefreshToken =
                refreshTokenService.create(user);

        String newAccessToken =
                generateAccessToken(user);

        return buildResponse(
                user,
                newAccessToken,
                newRefreshToken.getToken()
        );
    }

    @Transactional
    public void logout(
            RefreshTokenRequest request
    ) {
        refreshTokenService.revoke(
                request.refreshToken()
        );
    }

    private String generateAccessToken(User user) {

        return jwtService.generateToken(
                Map.of(
                        "role", user.getRole().name(),
                        "userId", user.getId(),
                        "tokenVersion", user.getTokenVersion()
                ),
                user
        );
    }

    private AuthResponse buildResponse(
            User user,
            String accessToken,
            String refreshToken
    ) {
        return new AuthResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                accessToken,
                refreshToken,
                "Bearer",
                ACCESS_TOKEN_EXPIRATION_SECONDS
        );
    }
}