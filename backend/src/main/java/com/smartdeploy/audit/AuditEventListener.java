package com.smartdeploy.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Enregistre les événements d'audit dans le journal.
 *
 * - Après validation de la transaction métier : seules les actions réellement
 *   effectuées sont tracées (fallbackExecution : événements hors transaction).
 * - Après annulation, uniquement pour les événements « keepOnRollback ».
 * - Chaîne d'empreintes : les écritures sont sérialisées, verrou tenu jusqu'à
 *   la validation de l'entrée, pour que deux entrées ne référencent jamais
 *   la même entrée précédente.
 * - Une erreur d'écriture du journal n'annule jamais l'action métier.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    static final String SYSTEM_ACTOR = "Système";
    static final String JENKINS_ACTOR = "Jenkins";

    /** Détection : au-delà de ce nombre d'échecs dans la fenêtre, activité suspecte. */
    static final int SUSPICIOUS_THRESHOLD = 5;
    static final Duration SUSPICIOUS_WINDOW = Duration.ofMinutes(10);

    private final AuditLogRepository auditLogRepository;
    private final TransactionOperations transactions;
    private final Object chainLock = new Object();

    /** Facultatif : publie AuditRecordedEvent (notifications) après chaque entrée enregistrée. */
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    public void setEventPublisher(org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    private void announce(AuditLog entry) {
        if (eventPublisher != null && entry != null) {
            eventPublisher.publishEvent(new AuditRecordedEvent(entry));
        }
    }

    @Autowired
    public AuditEventListener(AuditLogRepository auditLogRepository, PlatformTransactionManager transactionManager) {
        this(auditLogRepository, requiresNew(transactionManager));
    }

    AuditEventListener(AuditLogRepository auditLogRepository, TransactionOperations transactions) {
        this.auditLogRepository = auditLogRepository;
        this.transactions = transactions;
    }

    private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommitted(AuditEvent event) {
        record(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onRolledBack(AuditEvent event) {
        if (event.keepOnRollback()) {
            record(event);
        }
    }

    void record(AuditEvent event) {
        AuditRequestContext context = AuditRequestContext.current();
        String actor = truncate(resolveActor(event.actor()), 255);
        try {
            AuditLog saved = append(event, actor, context);
            announce(saved);
            detectSuspiciousActivity(saved);
        } catch (RuntimeException exception) {
            log.error("Écriture du journal d'audit impossible pour {}", event.action(), exception);
        }
    }

    private AuditLog append(AuditEvent event, String actor, AuditRequestContext context) {
        synchronized (chainLock) {
            return transactions.execute(status -> {
                String previousHash = auditLogRepository.findTopByOrderByIdDesc()
                        .map(AuditLog::getHash)
                        .orElse(null);

                AuditLog entry = AuditLog.builder()
                        .action(event.action())
                        .actor(actor)
                        .targetType(event.targetType())
                        .targetId(event.targetId())
                        .applicationName(truncate(event.applicationName(), 255))
                        .environmentName(truncate(event.environmentName(), 255))
                        .summary(truncate(event.summary() == null ? event.action().name() : event.summary(), 1000))
                        .details(truncate(event.details(), 2000))
                        .ipAddress(context.ipAddress())
                        .userAgent(context.userAgent())
                        // Microsecondes : précision conservée par PostgreSQL, sinon
                        // l'empreinte recalculée après relecture serait différente.
                        .occurredAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS))
                        .previousHash(previousHash == null ? AuditChain.GENESIS : previousHash)
                        .build();
                entry.setHash(AuditChain.hash(entry));
                return auditLogRepository.saveAndFlush(entry);
            });
        }
    }

    /**
     * Échecs répétés (connexion ou clé API Jenkins) sur une courte période :
     * une entrée SUSPICIOUS_ACTIVITY est ajoutée, une seule fois par fenêtre.
     */
    private void detectSuspiciousActivity(AuditLog entry) {
        if (entry == null) {
            return;
        }
        LocalDateTime since = entry.getOccurredAt().minus(SUSPICIOUS_WINDOW);

        if (entry.getAction() == AuditAction.LOGIN_FAILURE) {
            long byAccount = auditLogRepository.countByActionAndActorAndOccurredAtAfter(
                    AuditAction.LOGIN_FAILURE, entry.getActor(), since);
            long byIp = entry.getIpAddress() == null ? 0
                    : auditLogRepository.countByActionAndIpAddressAndOccurredAtAfter(
                    AuditAction.LOGIN_FAILURE, entry.getIpAddress(), since);
            if (byAccount >= SUSPICIOUS_THRESHOLD || byIp >= SUSPICIOUS_THRESHOLD) {
                raiseOnce(entry, since, "Tentatives de connexion répétées : "
                        + Math.max(byAccount, byIp) + " échecs en "
                        + SUSPICIOUS_WINDOW.toMinutes() + " minutes"
                        + (entry.getIpAddress() == null ? "" : " depuis " + entry.getIpAddress()));
            }
        }

        if (entry.getAction() == AuditAction.API_KEY_REJECTED && entry.getIpAddress() != null) {
            long byIp = auditLogRepository.countByActionAndIpAddressAndOccurredAtAfter(
                    AuditAction.API_KEY_REJECTED, entry.getIpAddress(), since);
            if (byIp >= SUSPICIOUS_THRESHOLD) {
                raiseOnce(entry, since, "Clé API Jenkins refusée " + byIp + " fois en "
                        + SUSPICIOUS_WINDOW.toMinutes() + " minutes depuis " + entry.getIpAddress());
            }
        }
    }

    private void raiseOnce(AuditLog trigger, LocalDateTime since, String summary) {
        if (auditLogRepository.existsByActionAndActorAndOccurredAtAfter(
                AuditAction.SUSPICIOUS_ACTIVITY, trigger.getActor(), since)) {
            return;
        }
        AuditLog alert = append(new AuditEvent(
                AuditAction.SUSPICIOUS_ACTIVITY, trigger.getActor(), "USER", null, null, null,
                summary, "Détection automatique sur le journal d'activité", true
        ), trigger.getActor(), new AuditRequestContext(trigger.getIpAddress(), trigger.getUserAgent()));
        log.warn("Activité suspecte détectée : {}", alert == null ? summary : alert.getSummary());
        announce(alert);
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
