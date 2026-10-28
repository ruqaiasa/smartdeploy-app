package com.smartdeploy.dashboard;

import com.smartdeploy.alert.AlertRepository;
import com.smartdeploy.alert.AlertStatus;
import com.smartdeploy.application.ApplicationRepository;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.EnvironmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ApplicationRepository applicationRepository;
    private final EnvironmentRepository environmentRepository;
    private final DeploymentRepository deploymentRepository;
    private final AlertRepository alertRepository;

    public DashboardService(
            ApplicationRepository applicationRepository,
            EnvironmentRepository environmentRepository,
            DeploymentRepository deploymentRepository,
            AlertRepository alertRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.environmentRepository = environmentRepository;
        this.deploymentRepository = deploymentRepository;
        this.alertRepository = alertRepository;
    }

    public DashboardResponse getDashboard() {

        long totalApplications =
                applicationRepository.countByDeletedFalse();

        long activeApplications =
                applicationRepository.countByActiveTrueAndDeletedFalse();

        long totalEnvironments =
                environmentRepository.countByDeletedFalse();

        long activeEnvironments =
                environmentRepository.countByActiveTrueAndDeletedFalse();

        long totalDeployments =
                deploymentRepository.countByDeletedFalse();

        long successfulDeployments =
                deploymentRepository.countByStatusAndDeletedFalse(
                        DeploymentStatus.SUCCESS
                );

        long failedDeployments =
                deploymentRepository.countByStatusAndDeletedFalse(
                        DeploymentStatus.FAILED
                );

        long completedDeployments =
                successfulDeployments + failedDeployments;

        double successRate =
                completedDeployments == 0
                        ? 0.0
                        : Math.round(
                        (
                                successfulDeployments
                                        * 10000.0
                        )
                                / completedDeployments
                ) / 100.0;

        long openAlerts =
                alertRepository.countByStatus(
                        AlertStatus.OPEN
                );

        List<DashboardResponse.RecentDeployment>
                recentDeployments =
                deploymentRepository
                        .findTop5ByDeletedFalseOrderByCreatedAtDesc()
                        .stream()
                        .map(this::toRecentDeployment)
                        .toList();

        return new DashboardResponse(
                totalApplications,
                activeApplications,
                totalEnvironments,
                activeEnvironments,
                totalDeployments,
                successfulDeployments,
                failedDeployments,
                successRate,
                openAlerts,
                recentDeployments
        );
    }

    private DashboardResponse.RecentDeployment
    toRecentDeployment(Deployment deployment) {

        return new DashboardResponse.RecentDeployment(
                deployment.getId(),

                deployment
                        .getEnvironment()
                        .getApplication()
                        .getId(),

                deployment
                        .getEnvironment()
                        .getApplication()
                        .getName(),

                deployment
                        .getVersion()
                        .getVersion(),

                deployment
                        .getEnvironment()
                        .getId(),

                deployment
                        .getEnvironment()
                        .getName(),

                deployment
                        .getEnvironment()
                        .getType()
                        .name(),

                deployment.getStatus(),

                deployment.getCreatedAt(),
                deployment.getFinishedAt()
        );
    }
}