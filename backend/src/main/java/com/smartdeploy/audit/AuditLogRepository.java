package com.smartdeploy.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Dernière entrée : son empreinte devient le previousHash de la suivante. */
    Optional<AuditLog> findTopByOrderByIdDesc();

    long countByActionAndActorAndOccurredAtAfter(AuditAction action, String actor, LocalDateTime after);

    long countByActionAndIpAddressAndOccurredAtAfter(AuditAction action, String ipAddress, LocalDateTime after);

    boolean existsByActionAndActorAndOccurredAtAfter(AuditAction action, String actor, LocalDateTime after);
}
