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
        LocalDateTime updatedAt,
        boolean pendingApproval,
        String accessRequest
) {
    public UserResponse(Long id, String firstName, String lastName, String email, Role role, boolean enabled,
                        boolean deleted, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, firstName, lastName, email, role, enabled, deleted, createdAt, updatedAt, false, null);
    }
}
