package com.smartdeploy.audit;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        AuditAction action,
        String actor,
        String targetType,
        Long targetId,
        String applicationName,
        String environmentName,
        String summary,
        LocalDateTime occurredAt
) {
    static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getAction(), log.getActor(), log.getTargetType(),
                log.getTargetId(), log.getApplicationName(), log.getEnvironmentName(),
                log.getSummary(), log.getOccurredAt()
        );
    }
}
