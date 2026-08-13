package com.smartdeploy.application;

import java.time.LocalDateTime;

public record ApplicationResponse(

        Long id,

        String name,

        String description,

        String repositoryUrl,

        Technology technology,

        boolean active,
        boolean deleted,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}