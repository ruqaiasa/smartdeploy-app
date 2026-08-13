package com.smartdeploy.auth;

import com.smartdeploy.user.Role;

public record AuthResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Role role,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}