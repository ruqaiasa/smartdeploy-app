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
        String builtCommit,

        // Approbation avant la production : qui a décidé, quand, et pourquoi
        String approvedBy,
        LocalDateTime approvalDecidedAt,
        String approvalComment,

        // Portail développeur : message de la demande
        String requestMessage
) {
    /** Compatibilité : sans message de demande. */
    public DeploymentResponse(Long id, Long applicationId, String applicationName, Long versionId, String version,
                              Long environmentId, String environmentName, String environmentType, Long requestedById,
                              String requestedByEmail, DeploymentStatus status, LocalDateTime startedAt,
                              LocalDateTime finishedAt, Long durationSeconds, Integer jenkinsBuildNumber,
                              String deploymentLogsUrl, String errorMessage, Long rollbackOfId, boolean deleted,
                              LocalDateTime createdAt, LocalDateTime updatedAt, String deployedBackendImage,
                              String deployedFrontendImage, boolean current, Long imagesFromId, String builtCommit,
                              String approvedBy, LocalDateTime approvalDecidedAt, String approvalComment) {
        this(id, applicationId, applicationName, versionId, version, environmentId, environmentName, environmentType,
                requestedById, requestedByEmail, status, startedAt, finishedAt, durationSeconds, jenkinsBuildNumber,
                deploymentLogsUrl, errorMessage, rollbackOfId, deleted, createdAt, updatedAt, deployedBackendImage,
                deployedFrontendImage, current, imagesFromId, builtCommit, approvedBy, approvalDecidedAt, approvalComment, null);
    }

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
                deployedFrontendImage, current, null, null, null, null, null);
    }
}
