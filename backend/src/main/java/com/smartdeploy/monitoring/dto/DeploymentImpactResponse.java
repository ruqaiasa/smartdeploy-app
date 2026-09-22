package com.smartdeploy.monitoring.dto;

import com.smartdeploy.monitoring.impact.DeploymentImpactStatus;

public record DeploymentImpactResponse(

        Long deploymentId,

        double averageCpuBefore,
        double averageCpuAfter,
        double cpuChangePercent,

        double averageMemoryBefore,
        double averageMemoryAfter,
        double memoryChangePercent,

        DeploymentImpactStatus status

) {
}