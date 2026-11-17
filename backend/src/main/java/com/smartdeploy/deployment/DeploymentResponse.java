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

        LocalDateTime updatedAt,
        String deployedBackendImage,
        String deployedFrontendImage,

        // Vrai pour le déploiement actuellement en service dans son environnement
        boolean current,

        // Promotion : déploiement dont les images ont été réutilisées (null : images construites)
        Long imagesFromId,

        // Commit exact construit par le pipeline
        String builtCommit
) {
    /** Compatibilité : sans promotion ni commit connu. */
    public DeploymentResponse(Long id, Long applicationId, String applicationName, Long versionId, String version,
                              Long environmentId, String environmentName, String environmentType, Long requestedById,
                              String requestedByEmail, DeploymentStatus status, LocalDateTime startedAt,
                              LocalDateTime finishedAt, Long durationSeconds, Integer jenkinsBuildNumber,
                              String deploymentLogsUrl, String errorMessage, Long rollbackOfId, boolean deleted,
                              LocalDateTime createdAt, LocalDateTime updatedAt, String deployedBackendImage,
                              String deployedFrontendImage, boolean current) {
        this(id, applicationId, applicationName, versionId, version, environmentId, environmentName, environmentType,
                requestedById, requestedByEmail, status, startedAt, finishedAt, durationSeconds, jenkinsBuildNumber,
                deploymentLogsUrl, errorMessage, rollbackOfId, deleted, createdAt, updatedAt, deployedBackendImage,
                deployedFrontendImage, current, null, null);
    }
}
