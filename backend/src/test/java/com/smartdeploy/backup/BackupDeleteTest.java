package com.smartdeploy.backup;

import com.smartdeploy.application.Application;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Suppression d'une sauvegarde : jamais en cours, jamais la seule restaurable. */
class BackupDeleteTest {

    private VeleroClient velero;
    private BackupService service;

    private static GenericKubernetesResource backup(String name, String phase) {
        return new GenericKubernetesResourceBuilder()
                .withApiVersion("velero.io/v1").withKind("Backup")
                .withNewMetadata().withName(name).withNamespace("velero").endMetadata()
                .withAdditionalProperties(Map.of(
                        "spec", Map.of("includedNamespaces", List.of("sd-test-fullstack-demo")),
                        "status", Map.of("phase", phase)))
                .build();
    }

    @BeforeEach
    void setUp() {
        velero = mock(VeleroClient.class);
        EnvironmentRepository environments = mock(EnvironmentRepository.class);
        Environment testing = mock(Environment.class);
        Application app = mock(Application.class);
        when(app.getName()).thenReturn("fullstack-demo");
        when(testing.getNamespace()).thenReturn("sd-test-fullstack-demo");
        when(testing.getApplication()).thenReturn(app);
        when(testing.getName()).thenReturn("Testing");
        when(testing.getId()).thenReturn(2L);
        when(environments.findById(2L)).thenReturn(Optional.of(testing));
        service = new BackupService(velero, mock(VeleroProperties.class), environments, mock(BackupTasks.class),
                mock(ApplicationEventPublisher.class));
    }

    @Test
    void aFailedBackupIsDeleted() {
        when(velero.backups()).thenReturn(List.of(backup("nuit-1", "Completed"), backup("nuit-2", "PartiallyFailed")));

        service.delete(2L, "nuit-2", "ruqaia");

        verify(velero).deleteBackup("nuit-2");
    }

    @Test
    void aRunningBackupCannotBeInterrupted() {
        when(velero.backups()).thenReturn(List.of(backup("nuit-1", "Completed"), backup("en-cours", "InProgress")));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.delete(2L, "en-cours", "ruqaia"));

        assertTrue(e.getMessage().contains("en cours"));
        verify(velero, never()).deleteBackup(any());
    }

    @Test
    void theOnlyRestorableBackupIsKept() {
        when(velero.backups()).thenReturn(List.of(backup("nuit-1", "Completed"), backup("nuit-2", "Failed")));

        assertThrows(IllegalStateException.class, () -> service.delete(2L, "nuit-1", "ruqaia"));
        verify(velero, never()).deleteBackup(any());
    }
}
