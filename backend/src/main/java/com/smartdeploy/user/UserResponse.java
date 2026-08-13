package com.smartdeploy.user;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Role role,
        boolean enabled,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}