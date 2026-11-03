package com.smartdeploy.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private AuditLog saved(AuditEvent event, boolean rolledBack) {
        AuditEventListener listener = new AuditEventListener(auditLogRepository);
        if (rolledBack) {
            listener.onRolledBack(event);
        } else {
            listener.onCommitted(event);
        }
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldRecordEventWithExplicitActor() {
        AuditLog log = saved(AuditEvent.of(
                AuditAction.DEPLOYMENT_CREATED, "ruqaia@smartdeploy.com", "DEPLOYMENT", 12L,
                "fullstack-demo", "fullstack-demo-dev", "Déploiement #12 lancé"), false);

        assertEquals(AuditAction.DEPLOYMENT_CREATED, log.getAction());
        assertEquals("ruqaia@smartdeploy.com", log.getActor());
        assertEquals("fullstack-demo", log.getApplicationName());
        assertNotNull(log.getOccurredAt());
    }

    @Test
    void shouldUseAuthenticatedUserWhenActorIsMissing() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@smartdeploy.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        AuditLog log = saved(AuditEvent.of(
                AuditAction.DEPLOYMENT_DELETED, null, "DEPLOYMENT", 3L, null, null, "Supprimé"), false);

        assertEquals("admin@smartdeploy.com", log.getActor());
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
        AuditLog log = saved(AuditEvent.securityFailure(
                AuditAction.LOGIN_FAILURE, "intrus@example.com", "Échec de connexion"), true);
        assertEquals(AuditAction.LOGIN_FAILURE, log.getAction());

        AuditEventListener listener = new AuditEventListener(auditLogRepository);
        listener.onRolledBack(AuditEvent.of(
                AuditAction.DEPLOYMENT_CREATED, "a", "DEPLOYMENT", 1L, null, null, "annulé"));
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void shouldNeverPropagateStorageErrors() {
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("base indisponible"));
        AuditEventListener listener = new AuditEventListener(auditLogRepository);

        assertDoesNotThrow(() -> listener.onCommitted(AuditEvent.of(
                AuditAction.LOGIN_SUCCESS, "a@b.c", "USER", 1L, null, null, "Connexion réussie")));
    }
}
