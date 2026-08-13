package com.smartdeploy.deployment;

import java.time.LocalDateTime;

public record DeploymentResponse(

        Long id,

        Long applicationId,

        String applicationName,

        Long versionId,

        String version,

        Long environmentId,

        String environmentName,

        String environmentType,

        Long requestedById,

        String requestedByEmail,

        DeploymentStatus status,

        LocalDateTime startedAt,

        LocalDateTime finishedAt,

        Long durationSeconds,

        Integer jenkinsBuildNumber,

        String deploymentLogsUrl,

        String errorMessage,

        Long rollbackOfId,

        boolean deleted,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}