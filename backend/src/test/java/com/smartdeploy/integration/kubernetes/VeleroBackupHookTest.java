package com.smartdeploy.integration.kubernetes;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Toute base PostgreSQL générée produit un export cohérent avant chaque sauvegarde Velero. */
class VeleroBackupHookTest {

    @Test
    void shouldDeclareAPgDumpPreBackupHookOnThePostgresPods() throws Exception {
        String yaml = new KubernetesManifestGeneratorService(mock(KubernetesClient.class)).generateDatabase(
                "sd-dev-fullstack-demo",
                new KubernetesDatabaseRequest(KubernetesDatabaseRequest.DatabaseMode.MANAGED, "database", "user",
                        "postgres-password", "password", null, 5432, 1)).statefulSetYaml();

        Map<String, String> annotations = Serialization.unmarshal(yaml, StatefulSet.class)
                .getSpec().getTemplate().getMetadata().getAnnotations();

        assertEquals("postgresql", annotations.get("pre.hook.backup.velero.io/container"));
        assertEquals("Fail", annotations.get("pre.hook.backup.velero.io/on-error"));
        List<String> command = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(annotations.get("pre.hook.backup.velero.io/command"), List.class);
        assertEquals(List.of("/bin/bash", "-c"), command.subList(0, 2));
        assertTrue(command.get(2).startsWith("pg_dump -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\""));
        assertTrue(command.get(2).endsWith("/var/lib/postgresql/data/velero-dump.sql"), "l'export est déposé sur le volume sauvegardé");
    }
}
