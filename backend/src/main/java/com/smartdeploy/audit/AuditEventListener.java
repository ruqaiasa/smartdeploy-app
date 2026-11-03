package com.smartdeploy.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * Enregistre les événements d'audit dans le journal.
 *
 * - Après validation de la transaction : seules les actions réellement
 *   effectuées sont tracées (fallbackExecution : événements publiés hors
 *   transaction).
 * - Après annulation, pour les seuls événements « keepOnRollback »
 *   (échecs de connexion), qui doivent être tracés malgré l'échec.
 * - Dans sa propre transaction : une erreur d'écriture du journal est
 *   journalisée mais n'annule jamais l'action métier déjà validée.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    static final String SYSTEM_ACTOR = "Système";
    static final String JENKINS_ACTOR = "Jenkins";

    private final AuditLogRepository auditLogRepository;

    public AuditEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCommitted(AuditEvent event) {
        record(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRolledBack(AuditEvent event) {
        if (event.keepOnRollback()) {
            record(event);
        }
    }

    void record(AuditEvent event) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .action(event.action())
                    .actor(truncate(resolveActor(event.actor()), 255))
                    .targetType(event.targetType())
                    .targetId(event.targetId())
                    .applicationName(truncate(event.applicationName(), 255))
                    .environmentName(truncate(event.environmentName(), 255))
                    .summary(truncate(event.summary() == null ? event.action().name() : event.summary(), 1000))
                    .occurredAt(LocalDateTime.now())
                    .build());
        } catch (RuntimeException exception) {
            log.error("Écriture du journal d'audit impossible pour {}", event.action(), exception);
        }
    }

    static String resolveActor(String actor) {
        if (actor != null && !actor.isBlank()) {
            return actor;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM_ACTOR;
        }
        if ("jenkins-service".equals(authentication.getName())) {
            return JENKINS_ACTOR;
        }
        return authentication.getName();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
