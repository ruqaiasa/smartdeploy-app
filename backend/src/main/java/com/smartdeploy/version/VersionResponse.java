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

        LocalDateTime updatedAt

) {
}