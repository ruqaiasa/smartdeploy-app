package com.smartdeploy.auth;

import com.smartdeploy.config.SecurityConfig;
import com.smartdeploy.security.jwt.CustomAccessDeniedHandler;
import com.smartdeploy.security.jwt.JwtAuthenticationEntryPoint;
import com.smartdeploy.security.jwt.JwtAuthenticationFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.smartdeploy.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        AuthController authController =
                new AuthController(authService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .build();
    }
    @Test
    void login_shouldReturn200AndAuthResponse() throws Exception {

        LoginRequest request = new LoginRequest(
                "admin@smartdeploy.com",
                "Password123"
        );

        AuthResponse response = new AuthResponse(
                1L,
                "Admin",
                "User",
                "admin@smartdeploy.com",
                Role.ADMIN,
                "access-token-value",
                "refresh-token-value",
                "Bearer",
                3600
        );

        when(authService.login(request))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email": "admin@smartdeploy.com",
                                      "password": "Password123"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Admin"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.email")
                        .value("admin@smartdeploy.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.accessToken")
                        .value("access-token-value"))
                .andExpect(jsonPath("$.refreshToken")
                        .value("refresh-token-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }
    @Test
    void login_shouldReturn400WhenRequestIsInvalid() throws Exception {

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email": "email-invalide",
                                      "password": "123"
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_shouldReturn200AndNewTokens() throws Exception {

        RefreshTokenRequest request =
                new RefreshTokenRequest("old-refresh-token");

        AuthResponse response = new AuthResponse(
                1L,
                "Admin",
                "User",
                "admin@smartdeploy.com",
                Role.ADMIN,
                "new-access-token",
                "new-refresh-token",
                "Bearer",
                3600
        );

        when(authService.refresh(request))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "refreshToken": "old-refresh-token"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken")
                        .value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken")
                        .value("new-refresh-token"))
                .andExpect(jsonPath("$.tokenType")
                        .value("Bearer"))
                .andExpect(jsonPath("$.expiresIn")
                        .value(3600));
    }
    @Test
    void logout_shouldReturn204NoContent() throws Exception {

        doNothing()
                .when(authService)
                .logout(new RefreshTokenRequest("refresh-token-value"));

        mockMvc.perform(
                        post("/api/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "refreshToken": "refresh-token-value"
                                    }
                                    """)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(authService)
                .logout(new RefreshTokenRequest("refresh-token-value"));
    }


}
