package com.smartdeploy.dashboard;

import com.smartdeploy.deployment.DeploymentStatus;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(

        long totalApplications,
        long activeApplications,

        long totalEnvironments,
        long activeEnvironments,

        long totalDeployments,
        long successfulDeployments,
        long failedDeployments,

        double successRate,

        long openAlerts,

        List<RecentDeployment> recentDeployments

) {

    public record RecentDeployment(

            Long id,

            Long applicationId,
            String applicationName,

            String version,

            Long environmentId,
            String environmentName,
            String environmentType,

            DeploymentStatus status,

            LocalDateTime createdAt,
            LocalDateTime finishedAt

    ) {
    }
}