package com.smartdeploy.version;

import java.time.LocalDateTime;

public record VersionResponse(

        Long id,

        Long applicationId,

        String applicationName,

        String version,

        String gitBranch,

        String gitCommit,

        String dockerImage,

        BuildStatus buildStatus,

        Integer buildNumber,

        String artifactPath,

        String buildLogsUrl,

        boolean deleted,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,

        // RG-V1, RG-V2 : figée à sa première construction réussie (commit et image fixés)
        LocalDateTime pinnedAt,
        boolean pinned,

        // RG-V10 : où elle est en service, et le verdict de son dernier déploiement
        java.util.List<String> inServiceEnvironments,
        String lastVerdict,
        String lastVerdictTone
) {
    /** Compatibilité : sans les informations d'usage. */
    public VersionResponse(Long id, Long applicationId, String applicationName, String version, String gitBranch,
                           String gitCommit, String dockerImage, BuildStatus buildStatus, Integer buildNumber,
                           String artifactPath, String buildLogsUrl, boolean deleted, LocalDateTime createdAt,
                           LocalDateTime updatedAt) {
        this(id, applicationId, applicationName, version, gitBranch, gitCommit, dockerImage, buildStatus, buildNumber,
                artifactPath, buildLogsUrl, deleted, createdAt, updatedAt, null, false, java.util.List.of(), null, null);
    }
}
