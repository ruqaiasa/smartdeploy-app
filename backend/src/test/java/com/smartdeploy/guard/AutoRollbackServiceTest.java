package com.smartdeploy.guard;

import com.smartdeploy.application.Application;
import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentResponse;
import com.smartdeploy.deployment.DeploymentService;
import com.smartdeploy.deployment.DeploymentStateException;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentType;
import com.smartdeploy.user.User;
import com.smartdeploy.version.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoRollbackServiceTest {

    @Mock private DeploymentGuardRepository guardRepository;
    @Mock private DeploymentRepository deploymentRepository;
    @Mock private DeploymentService deploymentService;
    @Mock private ApplicationEventPublisher publisher;

    private Environment environment;
    private Deployment degraded;
    private User requester;
    private DeploymentGuard guard;

    private AutoRollbackService service(String environments) {
        return new AutoRollbackService(guardRepository, deploymentRepository, deploymentService,
                new GuardProperties(true, 10, 15, 2, environments), publisher, TransactionOperations.withoutTransaction());
    }

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
        when(environment.getType()).thenReturn(EnvironmentType.DEVELOPMENT);
        when(environment.getName()).thenReturn("fullstack-demo-dev");
        Version version = mock(Version.class);
        Application application = mock(Application.class);
        when(version.getApplication()).thenReturn(application);
        when(application.getName()).thenReturn("fullstack-demo");
        requester = mock(User.class);
        degraded = mock(Deployment.class);
        when(degraded.getId()).thenReturn(117L);
        when(degraded.getEnvironment()).thenReturn(environment);
        when(degraded.getVersion()).thenReturn(version);
        when(degraded.getRequestedBy()).thenReturn(requester);
        when(deploymentRepository.findByIdAndDeletedFalse(117L)).thenReturn(Optional.of(degraded));
        guard = DeploymentGuard.builder().id(9L).deploymentId(117L).status(GuardStatus.DEGRADED).rollbackTargetId(116L).build();
        when(guardRepository.findFirstByDeploymentIdOrderByIdDesc(117L)).thenReturn(Optional.of(guard));
    }

    @Test
    void shouldRollBackAutomaticallyOnAnAllowedEnvironment() {
        DeploymentResponse rollback = mock(DeploymentResponse.class);
        when(rollback.id()).thenReturn(130L);
        when(deploymentService.createRollback(116L, requester)).thenReturn(rollback);

        service("DEVELOPMENT,TEST").rollBackIfAllowed(117L);

        verify(deploymentService).createRollback(116L, requester);
        assertEquals(130L, guard.getAutoRollbackDeploymentId());
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals(AuditAction.GUARD_AUTO_ROLLBACK, event.getValue().action());
        assertTrue(event.getValue().summary().contains("#130"));
    }

    @Test
    void shouldOnlyProposeTheRollbackInProduction() {
        when(environment.getType()).thenReturn(EnvironmentType.PRODUCTION);

        service("DEVELOPMENT,TEST").rollBackIfAllowed(117L);

        verify(deploymentService, never()).createRollback(anyLong(), any());
    }

    @Test
    void shouldDoNothingWithoutAHealthyTarget() {
        guard.setRollbackTargetId(null);

        service("DEVELOPMENT").rollBackIfAllowed(117L);

        verify(deploymentService, never()).createRollback(anyLong(), any());
    }

    @Test
    void shouldNeverRollBackTwiceForTheSameDegradation() {
        guard.setAutoRollbackDeploymentId(130L);

        service("DEVELOPMENT").rollBackIfAllowed(117L);

        verify(deploymentService, never()).createRollback(anyLong(), any());
    }

    @Test
    void shouldTraceTheReasonWhenTheRollbackIsRefused() {
        when(deploymentService.createRollback(116L, requester))
                .thenThrow(new DeploymentStateException("Un déploiement est déjà en attente ou en cours sur cet environnement"));

        assertDoesNotThrow(() -> service("DEVELOPMENT").rollBackIfAllowed(117L));

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertTrue(event.getValue().summary().startsWith("Retour arrière automatique impossible"));
    }

    @Test
    void shouldParseTheAllowedEnvironments() {
        GuardProperties properties = new GuardProperties(true, 10, 15, 2, "DEVELOPMENT, TEST,INCONNU");
        assertTrue(properties.autoRollback(EnvironmentType.TEST));
        assertFalse(properties.autoRollback(EnvironmentType.PRODUCTION));
    }
}
