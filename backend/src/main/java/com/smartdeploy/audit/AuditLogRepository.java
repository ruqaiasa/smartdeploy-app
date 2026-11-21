package com.smartdeploy.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Dernière entrée : son empreinte devient le previousHash de la suivante. */
    Optional<AuditLog> findTopByOrderByIdDesc();

    /** Dernière alerte d'une surveillance pour une cible (pour ne pas la répéter après un redémarrage). */
    Optional<AuditLog> findTopByTargetTypeAndTargetIdAndActionInOrderByIdDesc(
            String targetType, Long targetId, java.util.Collection<AuditAction> actions);

    Optional<AuditLog> findTopByTargetTypeAndActionInOrderByIdDesc(String targetType, java.util.Collection<AuditAction> actions);

    Optional<AuditLog> findTopByActionInAndSummaryContainingOrderByIdDesc(java.util.Collection<AuditAction> actions, String text);

    long countByActionAndActorAndOccurredAtAfter(AuditAction action, String actor, LocalDateTime after);

    long countByActionAndIpAddressAndOccurredAtAfter(AuditAction action, String ipAddress, LocalDateTime after);

    boolean existsByActionAndActorAndOccurredAtAfter(AuditAction action, String actor, LocalDateTime after);
}
