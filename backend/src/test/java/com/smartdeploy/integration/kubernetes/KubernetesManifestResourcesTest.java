package com.smartdeploy.integration.kubernetes;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Les conteneurs générés déclarent des ressources réservées et maximales. */
class KubernetesManifestResourcesTest {

    private final KubernetesManifestGeneratorService generator =
            new KubernetesManifestGeneratorService(mock(KubernetesClient.class));

    private static Container container(String yaml) {
        return Serialization.unmarshal(yaml, Deployment.class).getSpec().getTemplate().getSpec().getContainers().get(0);
    }

    @Test
    void shouldDeclareResourcesForBackendAndFrontend() {
        Map<String, String> manifests = generator.generateApplication(new KubernetesApplicationRequest(
                "sd-dev-fullstack-demo",
                new KubernetesComponentRequest("fullstack-demo-frontend", "registry/frontend@sha256:aaa", 2, 8080),
                new KubernetesComponentRequest("fullstack-demo-backend", "registry/backend@sha256:bbb", 2, 8080),
                null,
                null
        )).manifests();

        Container backend = container(manifests.get("backend-deployment.yaml"));
        assertEquals("1Gi", backend.getResources().getLimits().get("memory").toString());
        assertEquals("384Mi", backend.getResources().getRequests().get("memory").toString());
        assertEquals("1000m", backend.getResources().getLimits().get("cpu").toString());

        Container frontend = container(manifests.get("frontend-deployment.yaml"));
        assertEquals("128Mi", frontend.getResources().getLimits().get("memory").toString());
        assertEquals("25m", frontend.getResources().getRequests().get("cpu").toString());
    }

    @Test
    void shouldKeepProbesAndImageWhenAddingResources() {
        String yaml = generator.withResources(generator.generateDeployment(new KubernetesDeploymentConfigRequest(
                "fullstack-demo-backend", "sd-dev-fullstack-demo", "registry/backend@sha256:bbb", 1, 8080)),
                ContainerResourceProfile.BACKEND);

        Container backend = container(yaml);
        assertEquals("registry/backend@sha256:bbb", backend.getImage());
        assertNotNull(backend.getStartupProbe());
        assertNotNull(backend.getReadinessProbe());
        assertNotNull(backend.getLivenessProbe());
    }
}
