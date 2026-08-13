package com.smartdeploy.auth;

import com.smartdeploy.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshExpiration;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${security.jwt.refresh-expiration}")
            long refreshExpiration
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpiration = refreshExpiration;
    }

    public RefreshToken create(User user) {

        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .revoked(false)
                .expiresAt(
                        LocalDateTime.now()
                                .plusNanos(refreshExpiration * 1_000_000)
                )
                .build();

        return refreshTokenRepository.saveAndFlush(refreshToken);
    }

    @Transactional(readOnly = true)
    public RefreshToken verify(String token) {

        RefreshToken refreshToken =
                refreshTokenRepository.findByToken(token)
                        .orElseThrow(() ->
                                new InvalidRefreshTokenException(
                                        "Refresh token invalide"
                                )
                        );

        if (!refreshToken.isValid()) {
            throw new InvalidRefreshTokenException(
                    "Refresh token expiré ou révoqué"
            );
        }

        return refreshToken;
    }

    public void revoke(String token) {

        RefreshToken refreshToken = verify(token);
        refreshToken.setRevoked(true);

        refreshTokenRepository.saveAndFlush(refreshToken);
    }

    public void revokeAll(User user) {
        refreshTokenRepository.deleteAllByUser(user);
    }
}