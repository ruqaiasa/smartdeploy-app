package com.smartdeploy.guard;

import java.time.LocalDateTime;
import java.util.List;

public record DeploymentGuardResponse(
        Long id,
        Long deploymentId,
        GuardStatus status,
        String verdict,
        List<GuardCheck> checks,
        int evaluations,
        Long rollbackTargetId,
        Long autoRollbackDeploymentId,
        LocalDateTime startedAt,
        LocalDateTime endsAt,
        LocalDateTime lastEvaluatedAt,
        LocalDateTime decidedAt
) {
}
