package com.smartdeploy.backup;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BackupNamesTest {

    private static GenericKubernetesResource backup(Map<String, String> labels, Map<String, Object> spec, Map<String, Object> status) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("spec", spec);
        if (status != null) {
            extra.put("status", status);
        }
        return new GenericKubernetesResourceBuilder().withApiVersion("velero.io/v1").withKind("Backup")
                .withNewMetadata().withName("b").addToLabels(labels).endMetadata()
                .withAdditionalProperties(extra).build();
    }

    @Test
    void shouldBuildKubernetesCompliantNames() {
        String name = BackupNames.name("avant-deploiement-117", "Fullstack Demo DEV", LocalDateTime.of(2026, 11, 5, 21, 30, 5));
        assertEquals("avant-deploiement-117-fullstack-demo-dev-20261105-213005", name);
        String longName = BackupNames.name("manuelle", "x".repeat(80), LocalDateTime.of(2026, 11, 5, 21, 30, 5));
        assertTrue(longName.length() <= 63);
        assertTrue(longName.matches("[a-z0-9-]+"));
    }

    @Test
    void shouldRecognizeExplicitAndScheduledCoverage() {
        GenericKubernetesResource explicit = backup(Map.of(), Map.of("includedNamespaces", List.of("sd-dev-fullstack-demo")), null);
        assertTrue(BackupNames.covers(explicit, "sd-dev-fullstack-demo"));
        assertFalse(BackupNames.covers(explicit, "sd-prod-autre"));

        GenericKubernetesResource scheduled = backup(Map.of("velero.io/schedule-name", "sauvegarde-quotidienne"),
                Map.of("excludedNamespaces", List.of("kube-system", "velero", "monitoring")), null);
        assertTrue(BackupNames.covers(scheduled, "sd-dev-fullstack-demo"));
        assertFalse(BackupNames.covers(scheduled, "monitoring"));
        assertEquals("SCHEDULED", BackupNames.origin(scheduled));
    }

    @Test
    void shouldReadTheVeleroStatus() {
        GenericKubernetesResource restorePoint = backup(
                Map.of("smartdeploy.io/reason", "pre-deployment", "smartdeploy.io/deployment-id", "117"),
                Map.of("includedNamespaces", List.of("sd-dev-fullstack-demo")),
                Map.of("phase", "Completed", "startTimestamp", "2026-11-05T19:44:23Z", "progress", Map.of("itemsBackedUp", 47)));

        BackupInfo info = BackupNames.toInfo(restorePoint);
        assertEquals("PRE_DEPLOYMENT", info.origin());
        assertEquals(117L, info.deploymentId());
        assertEquals("Completed", info.phase());
        assertEquals(47, info.itemsBackedUp());
    }
}
