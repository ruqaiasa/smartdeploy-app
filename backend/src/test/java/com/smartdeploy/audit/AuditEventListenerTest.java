package com.smartdeploy.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditEventListenerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuditEventListener(auditLogRepository, TransactionOperations.withoutTransaction());
        when(auditLogRepository.saveAndFlush(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private List<AuditLog> savedEntries() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, atLeastOnce()).saveAndFlush(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void shouldRecordEventWithActorAndValidHash() {
        listener.onCommitted(AuditEvent.of(
                AuditAction.DEPLOYMENT_CREATED, "ruqaia@smartdeploy.com", "DEPLOYMENT", 12L,
                "fullstack-demo", "fullstack-demo-dev", "Déploiement #12 lancé"));

        AuditLog log = savedEntries().get(0);
        assertEquals("ruqaia@smartdeploy.com", log.getActor());
        assertEquals(AuditChain.GENESIS, log.getPreviousHash());
        assertEquals(AuditChain.hash(log), log.getHash());
        assertEquals(0, log.getOccurredAt().getNano() % 1000, "précision à la microseconde");
    }

    @Test
    void shouldChainEachEntryToThePreviousOne() {
        AuditLog previous = AuditLog.builder().id(7L).hash("a".repeat(64)).build();
        when(auditLogRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(previous));

        listener.onCommitted(AuditEvent.of(AuditAction.LOGOUT, "a@b.c", "USER", null, null, null, "Déconnexion"));

        assertEquals("a".repeat(64), savedEntries().get(0).getPreviousHash());
    }

    @Test
    void shouldUseAuthenticatedUserWhenActorIsMissing() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@smartdeploy.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        listener.onCommitted(AuditEvent.of(AuditAction.DEPLOYMENT_DELETED, null, "DEPLOYMENT", 3L, null, null, "Supprimé"));

        assertEquals("admin@smartdeploy.com", savedEntries().get(0).getActor());
    }

    @Test
    void shouldNameJenkinsAndSystemActors() {
        assertEquals(AuditEventListener.SYSTEM_ACTOR, AuditEventListener.resolveActor(null));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "jenkins-service", null, List.of(new SimpleGrantedAuthority("ROLE_JENKINS"))));
        assertEquals(AuditEventListener.JENKINS_ACTOR, AuditEventListener.resolveActor(" "));
    }

    @Test
    void shouldKeepSecurityFailureAfterRollbackButNotOrdinaryEvents() {
        listener.onRolledBack(AuditEvent.of(AuditAction.DEPLOYMENT_CREATED, "a", "DEPLOYMENT", 1L, null, null, "annulé"));
        verify(auditLogRepository, never()).saveAndFlush(any(AuditLog.class));

        listener.onRolledBack(AuditEvent.securityFailure(AuditAction.LOGIN_FAILURE, "intrus@example.com", "Échec"));
        assertEquals(AuditAction.LOGIN_FAILURE, savedEntries().get(0).getAction());
    }

    @Test
    void shouldRaiseSuspiciousActivityOnceAfterRepeatedLoginFailures() {
        when(auditLogRepository.countByActionAndActorAndOccurredAtAfter(
                eq(AuditAction.LOGIN_FAILURE), eq("intrus@example.com"), any()))
                .thenReturn((long) AuditEventListener.SUSPICIOUS_THRESHOLD);
        when(auditLogRepository.existsByActionAndActorAndOccurredAtAfter(any(), any(), any())).thenReturn(false);

        listener.onRolledBack(AuditEvent.securityFailure(AuditAction.LOGIN_FAILURE, "intrus@example.com", "Échec"));

        List<AuditLog> saved = savedEntries();
        assertEquals(2, saved.size());
        assertEquals(AuditAction.SUSPICIOUS_ACTIVITY, saved.get(1).getAction());
        assertTrue(saved.get(1).getSummary().contains("Tentatives de connexion répétées"));
    }

    @Test
    void shouldNotRaiseSuspiciousActivityBelowThresholdOrTwice() {
        when(auditLogRepository.countByActionAndActorAndOccurredAtAfter(any(), any(), any())).thenReturn(2L);
        listener.onRolledBack(AuditEvent.securityFailure(AuditAction.LOGIN_FAILURE, "x@y.z", "Échec"));
        assertEquals(1, savedEntries().size());

        when(auditLogRepository.countByActionAndActorAndOccurredAtAfter(any(), any(), any())).thenReturn(9L);
        when(auditLogRepository.existsByActionAndActorAndOccurredAtAfter(any(), any(), any())).thenReturn(true);
        listener.onRolledBack(AuditEvent.securityFailure(AuditAction.LOGIN_FAILURE, "x@y.z", "Échec"));
        assertTrue(savedEntries().stream().noneMatch(e -> e.getAction() == AuditAction.SUSPICIOUS_ACTIVITY));
    }

    @Test
    void shouldNeverPropagateStorageErrors() {
        when(auditLogRepository.saveAndFlush(any(AuditLog.class))).thenThrow(new RuntimeException("base indisponible"));

        assertDoesNotThrow(() -> listener.onCommitted(AuditEvent.of(
                AuditAction.LOGIN_SUCCESS, "a@b.c", "USER", 1L, null, null, "Connexion réussie")));
    }
}
