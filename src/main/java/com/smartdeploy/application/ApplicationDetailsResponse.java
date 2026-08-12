package com.smartdeploy.application;

import com.smartdeploy.environment.EnvironmentResponse;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationDetailsResponse(

        Long id,

        String name,

        String description,

        String repositoryUrl,

        Technology technology,

        boolean active,

        boolean deleted,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,

        List<EnvironmentResponse> environments

) {
}