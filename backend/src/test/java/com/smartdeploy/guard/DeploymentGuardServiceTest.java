package com.smartdeploy.guard;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import com.smartdeploy.application.Application;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.Environment;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeploymentGuardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 4, 10, 0);

    @Mock private DeploymentGuardRepository guardRepository;
    @Mock private DeploymentRepository deploymentRepository;
    @Mock private GuardMetricsCollector collector;
    @Mock private ApplicationEventPublisher publisher;

    private final GuardProperties properties = new GuardProperties(true, 10, 15, 2);
    private Environment environment;
    private Deployment deployment;

    private DeploymentGuardService service(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        return new DeploymentGuardService(guardRepository, deploymentRepository, collector, properties, publisher,
                TransactionOperations.withoutTransaction(), clock);
    }

    private static Deployment deployment(long id, DeploymentStatus status, LocalDateTime createdAt, String image, Environment env) {
        Deployment d = mock(Deployment.class);
        Version version = mock(Version.class);
        Application application = mock(Application.class);
        when(d.getId()).thenReturn(id);
        when(d.getStatus()).thenReturn(status);
        when(d.getCreatedAt()).thenReturn(createdAt);
        when(d.getDeployedBackendImage()).thenReturn(image);
        when(d.getEnvironment()).thenReturn(env);
        when(d.getKubernetesDeploymentName()).thenReturn("fullstack-demo-backend");
        when(d.getVersion()).thenReturn(version);
        when(version.getVersion()).thenReturn("1.0." + id);
        when(version.getApplication()).thenReturn(application);
        when(application.getName()).thenReturn("fullstack-demo");
        return d;
    }

    private static DeploymentGuard watching() {
        return DeploymentGuard.builder().id(1L).deploymentId(112L).environmentId(5L)
                .namespace("sd-dev-fullstack-demo").applicationPrefix("fullstack-demo")
                .status(GuardStatus.WATCHING).startedAt(T0).endsAt(T0.plusMinutes(10)).build();
    }

    private static GuardEvaluation critical() {
        return GuardEvaluator.evaluate(new GuardSnapshot(3, 1, 4, List.of("backend-7f9 : CrashLoopBackOff"), List.of(), 0.3, 5.0, 300.0, 10.0, 400.0));
    }

    private static GuardEvaluation ok() {
        return GuardEvaluator.evaluate(new GuardSnapshot(3, 0, 0, List.of(), List.of(), 0.4, 12.0, 410.0, 10.0, 400.0));
    }

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
        when(environment.getId()).thenReturn(5L);
        when(environment.getNamespace()).thenReturn("sd-dev-fullstack-demo");
        when(environment.getName()).thenReturn("fullstack-demo-dev");
        deployment = deployment(112L, DeploymentStatus.SUCCESS, T0.minusMinutes(4), "registry/backend@sha256:aaa", environment);
        when(guardRepository.save(any(DeploymentGuard.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldStartWatchingAndSupersedeThePreviousGuard() {
        DeploymentGuard previous = watching();
        previous.setDeploymentId(111L);
        when(deploymentRepository.findByIdAndDeletedFalse(112L)).thenReturn(Optional.of(deployment));
        when(guardRepository.findByEnvironmentIdAndStatus(5L, GuardStatus.WATCHING)).thenReturn(List.of(previous));

        service(T0).start(112L);

        assertEquals(GuardStatus.SUPERSEDED, previous.getStatus());
        ArgumentCaptor<DeploymentGuard> saved = ArgumentCaptor.forClass(DeploymentGuard.class);
        verify(guardRepository).save(saved.capture());
        assertEquals(GuardStatus.WATCHING, saved.getValue().getStatus());
        assertEquals("fullstack-demo", saved.getValue().getApplicationPrefix());
        assertEquals(T0.plusMinutes(10), saved.getValue().getEndsAt());
    }

    @Test
    void shouldNotWatchAFailedDeployment() {
        Deployment failed = deployment(113L, DeploymentStatus.FAILED, T0, null, environment);
        when(deploymentRepository.findByIdAndDeletedFalse(113L)).thenReturn(Optional.of(failed));

        service(T0).start(113L);

        verify(guardRepository, never()).save(any());
    }

    @Test
    void shouldRequireTwoConsecutiveCriticalEvaluationsBeforeDegrading() {
        DeploymentGuard guard = watching();
        DeploymentGuardService service = service(T0.plusMinutes(1));

        service.apply(guard, deployment, critical(), true, T0.plusMinutes(1));
        assertEquals(GuardStatus.WATCHING, guard.getStatus(), "une seule évaluation critique ne suffit pas");

        service.apply(guard, deployment, ok(), true, T0.plusMinutes(2));
        assertEquals(0, guard.getConsecutiveCritical(), "un retour à la normale remet le compteur à zéro");

        service.apply(guard, deployment, critical(), true, T0.plusMinutes(3));
        service.apply(guard, deployment, critical(), true, T0.plusMinutes(3).plusSeconds(30));
        assertEquals(GuardStatus.DEGRADED, guard.getStatus());
    }

    @Test
    void shouldProposeRollbackToThePreviousReplacedDeploymentAndTraceIt() {
        Deployment older = deployment(105L, DeploymentStatus.ROLLED_BACK, T0.minusDays(2), "registry/backend@sha256:old", environment);
        Deployment previous = deployment(107L, DeploymentStatus.ROLLED_BACK, T0.minusDays(1), "registry/backend@sha256:prev", environment);
        Deployment untraced = deployment(108L, DeploymentStatus.ROLLED_BACK, T0.minusHours(5), null, environment);
        when(deploymentRepository.findByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(environment, DeploymentStatus.ROLLED_BACK))
                .thenReturn(List.of(untraced, previous, older));
        DeploymentGuard guard = watching();
        DeploymentGuardService service = service(T0.plusMinutes(2));

        service.apply(guard, deployment, critical(), true, T0.plusMinutes(2));
        service.apply(guard, deployment, critical(), true, T0.plusMinutes(2).plusSeconds(30));

        assertEquals(GuardStatus.DEGRADED, guard.getStatus());
        assertEquals(107L, guard.getRollbackTargetId(), "le plus récent déploiement remplacé et tracé par empreinte");
        assertTrue(guard.getVerdict().contains("Retour proposé au déploiement #107"));

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals(AuditAction.GUARD_DEGRADED, event.getValue().action());
        assertEquals(DeploymentGuardService.GUARD_ACTOR, event.getValue().actor());
        assertTrue(event.getValue().details().contains("CrashLoopBackOff"), "les preuves sont tracées");
    }

    @Test
    void shouldNotProposeARollbackForARollbackDeployment() {
        Deployment rollback = deployment(114L, DeploymentStatus.SUCCESS, T0, "registry/backend@sha256:x", environment);
        when(rollback.getRollbackOf()).thenReturn(mock(Deployment.class));

        assertTrue(service(T0).rollbackTarget(rollback).isEmpty());
    }

    @Test
    void shouldCloseHealthyAtTheEndOfTheWindow() {
        DeploymentGuard guard = watching();
        service(T0.plusMinutes(10)).apply(guard, deployment, ok(), true, T0.plusMinutes(10));

        assertEquals(GuardStatus.HEALTHY, guard.getStatus());
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals(AuditAction.GUARD_PASSED, event.getValue().action());
    }

    @Test
    void shouldBeInconclusiveWithoutAnyMetric() {
        DeploymentGuard guard = watching();
        service(T0.plusMinutes(10)).apply(guard, deployment, GuardEvaluator.evaluate(null), false, T0.plusMinutes(10));

        assertEquals(GuardStatus.INCONCLUSIVE, guard.getStatus());
    }

    @Test
    void shouldStopWatchingWhenTheDeploymentIsNoLongerInService() {
        DeploymentGuard guard = watching();
        Deployment replaced = deployment(112L, DeploymentStatus.ROLLED_BACK, T0, "img", environment);
        when(guardRepository.findById(1L)).thenReturn(Optional.of(guard));
        when(deploymentRepository.findByIdAndDeletedFalse(112L)).thenReturn(Optional.of(replaced));
        when(collector.collect(anyString(), anyString(), anyLong(), anyInt())).thenReturn(null);

        service(T0.plusMinutes(3)).evaluate(1L);

        assertEquals(GuardStatus.SUPERSEDED, guard.getStatus());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void shouldOnlyRetainPersistentWarnings() {
        GuardEvaluation warning = GuardEvaluator.evaluate(new GuardSnapshot(3, 0, 1, List.of(), List.of(), 0.4, 12.0, 410.0, 10.0, 400.0));
        DeploymentGuard guard = watching();
        DeploymentGuardService service = service(T0.plusMinutes(10));

        service.apply(guard, deployment, warning, true, T0.plusMinutes(4));
        service.apply(guard, deployment, ok(), true, T0.plusMinutes(5));
        service.apply(guard, deployment, ok(), true, T0.plusMinutes(10));
        assertEquals(GuardStatus.HEALTHY, guard.getStatus(), "un avertissement isolé n'émet pas de réserve");

        DeploymentGuard persistent = watching();
        for (int i = 0; i < DeploymentGuardService.PERSISTENT_WARNINGS; i++) {
            service.apply(persistent, deployment, warning, true, T0.plusMinutes(4).plusSeconds(30L * i));
        }
        service.apply(persistent, deployment, ok(), true, T0.plusMinutes(10));
        assertEquals(GuardStatus.HEALTHY_WITH_WARNINGS, persistent.getStatus());
    }

    @Test
    void shouldProposeTheDeploymentThatWasInServiceEvenIfStillMarkedSuccessful() {
        // Cas réel du déploiement #117 : le témoin #116 est resté « Réussi » (déploiement normal
        // ensuite), le #115 a été « Remplacé » par un retour arrière plus ancien.
        Deployment replacedByRollback = deployment(115L, DeploymentStatus.ROLLED_BACK, T0.minusMinutes(30), "registry/backend@sha256:cb6", environment);
        Deployment witness = deployment(116L, DeploymentStatus.SUCCESS, T0.minusMinutes(20), "registry/backend@sha256:20d", environment);
        when(deploymentRepository.findByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(environment, DeploymentStatus.SUCCESS))
                .thenReturn(List.of(deployment, witness));
        when(deploymentRepository.findByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(environment, DeploymentStatus.ROLLED_BACK))
                .thenReturn(List.of(replacedByRollback));

        assertEquals(116L, service(T0).rollbackTarget(deployment).orElseThrow().getId());
    }

    @Test
    void shouldNeverProposeADeploymentJudgedDegraded() {
        Deployment healthy = deployment(110L, DeploymentStatus.SUCCESS, T0.minusHours(2), "registry/backend@sha256:ok", environment);
        Deployment degraded = deployment(111L, DeploymentStatus.SUCCESS, T0.minusHours(1), "registry/backend@sha256:ko", environment);
        when(deploymentRepository.findByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(environment, DeploymentStatus.SUCCESS))
                .thenReturn(List.of(deployment, degraded, healthy));
        DeploymentGuard degradedGuard = watching();
        degradedGuard.setDeploymentId(111L);
        degradedGuard.setStatus(GuardStatus.DEGRADED);
        when(guardRepository.findFirstByDeploymentIdOrderByIdDesc(111L)).thenReturn(Optional.of(degradedGuard));

        assertEquals(110L, service(T0).rollbackTarget(deployment).orElseThrow().getId());
    }
}
