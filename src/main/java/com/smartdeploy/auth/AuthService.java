package com.smartdeploy.auth;

import com.smartdeploy.security.jwt.JwtService;
import com.smartdeploy.user.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final long ACCESS_TOKEN_EXPIRATION_SECONDS = 3600;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                normalizedEmail,
                                request.password()
                        )
                );

        User user = (User) authentication.getPrincipal();

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