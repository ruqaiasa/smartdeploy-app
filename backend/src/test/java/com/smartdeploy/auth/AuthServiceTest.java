package com.smartdeploy.auth;

import com.smartdeploy.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                jwtService,
                refreshTokenService
        );
    }

    @Test
    void login_shouldAuthenticateAndReturnTokens() {

        LoginRequest request = new LoginRequest(
                " ADMIN@SMARTDEPLOY.COM ",
                "Password123"
        );

        User user = User.builder()
                .id(1L)
                .firstName("Admin")
                .lastName("User")
                .email("admin@smartdeploy.com")
                .password("encoded-password")
                .role(Role.ADMIN)
                .enabled(true)
                .deleted(false)
                .tokenVersion(2)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Authentication authentication = mock(Authentication.class);

        RefreshToken refreshToken = RefreshToken.builder()
                .id(10L)
                .token("refresh-token-value")
                .user(user)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();

        when(authenticationManager.authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        )).thenReturn(authentication);

        when(authentication.getPrincipal())
                .thenReturn(user);

        when(jwtService.generateToken(
                anyMap(),
                eq(user)
        )).thenReturn("access-token-value");

        when(refreshTokenService.create(user))
                .thenReturn(refreshToken);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("Admin", response.firstName());
        assertEquals("User", response.lastName());
        assertEquals(
                "admin@smartdeploy.com",
                response.email()
        );
        assertEquals(Role.ADMIN, response.role());
        assertEquals(
                "access-token-value",
                response.accessToken()
        );
        assertEquals(
                "refresh-token-value",
                response.refreshToken()
        );
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600, response.expiresIn());

        verify(authenticationManager).authenticate(
                argThat(token ->
                        token.getPrincipal()
                                .equals("admin@smartdeploy.com")
                                && token.getCredentials()
                                .equals("Password123")
                )
        );

        verify(jwtService).generateToken(
                argThat(claims ->
                        claims.get("role").equals("ADMIN")
                                && claims.get("userId").equals(1L)
                                && claims.get("tokenVersion").equals(2)
                ),
                eq(user)
        );

        verify(refreshTokenService).create(user);
    }

    @Test
    void login_shouldThrowWhenCredentialsAreInvalid() {

        LoginRequest request = new LoginRequest(
                "user@smartdeploy.com",
                "WrongPassword"
        );

        when(authenticationManager.authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        )).thenThrow(
                new org.springframework.security.authentication.BadCredentialsException(
                        "Identifiants invalides"
                )
        );

        assertThrows(
                org.springframework.security.authentication.BadCredentialsException.class,
                () -> authService.login(request)
        );

        verify(jwtService, never())
                .generateToken(anyMap(), any(User.class));

        verify(refreshTokenService, never())
                .create(any(User.class));
    }
    @Test
    void refresh_shouldRevokeOldTokenAndReturnNewTokens() {

        RefreshTokenRequest request =
                new RefreshTokenRequest("old-refresh-token");

        User user = User.builder()
                .id(1L)
                .firstName("Admin")
                .lastName("User")
                .email("admin@smartdeploy.com")
                .password("encoded-password")
                .role(Role.ADMIN)
                .enabled(true)
                .deleted(false)
                .tokenVersion(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        RefreshToken oldRefreshToken = RefreshToken.builder()
                .id(10L)
                .token("old-refresh-token")
                .user(user)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();

        RefreshToken newRefreshToken = RefreshToken.builder()
                .id(11L)
                .token("new-refresh-token")
                .user(user)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();

        when(refreshTokenService.verify("old-refresh-token"))
                .thenReturn(oldRefreshToken);

        when(refreshTokenService.create(user))
                .thenReturn(newRefreshToken);

        when(jwtService.generateToken(
                anyMap(),
                eq(user)
        )).thenReturn("new-access-token");

        AuthResponse response = authService.refresh(request);

        assertNotNull(response);

        assertTrue(oldRefreshToken.isRevoked());

        assertEquals(
                "new-access-token",
                response.accessToken()
        );

        assertEquals(
                "new-refresh-token",
                response.refreshToken()
        );

        assertEquals("Bearer", response.tokenType());
        assertEquals(3600, response.expiresIn());

        verify(refreshTokenService)
                .verify("old-refresh-token");

        verify(refreshTokenService)
                .create(user);

        verify(jwtService)
                .generateToken(
                        argThat(claims ->
                                claims.get("role").equals("ADMIN")
                                        && claims.get("userId").equals(1L)
                                        && claims.get("tokenVersion").equals(3)
                        ),
                        eq(user)
                );
    }
    @Test
    void logout_shouldRevokeRefreshToken() {

        RefreshTokenRequest request =
                new RefreshTokenRequest("refresh-token-value");

        doNothing().when(refreshTokenService)
                .revoke("refresh-token-value");

        authService.logout(request);

        verify(refreshTokenService)
                .revoke("refresh-token-value");

        verifyNoMoreInteractions(
                jwtService,
                authenticationManager
        );
    }
}