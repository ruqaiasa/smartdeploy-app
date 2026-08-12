package com.smartdeploy.environment;

import java.time.LocalDateTime;

public record EnvironmentResponse(

        Long id,

        Long applicationId,

        String applicationName,

        EnvironmentType type,

        String name,

        String description,

        String namespace,

        String clusterUrl,

        boolean active,

        boolean deleted,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}