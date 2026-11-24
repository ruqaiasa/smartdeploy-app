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

    @Test
    void theFrontendIsToldTheRealAddressOfItsBackend() {
        // Cas réel : application nommée autrement que « fullstack-demo » ; l'image de l'interface portait une
        // adresse figée (fullstack-demo-backend-service) et Nginx s'arrêtait sur « host not found in upstream ».
        Map<String, String> manifests = generator.generateApplication(new KubernetesApplicationRequest(
                "sd-dev-fullstack-app-deploy-render",
                new KubernetesComponentRequest("fullstack-app-deploy-render-frontend", "registry/frontend@sha256:aaa", 2, 8080),
                new KubernetesComponentRequest("fullstack-app-deploy-render-backend", "registry/backend@sha256:bbb", 2, 8080),
                null,
                null
        )).manifests();

        Container frontend = container(manifests.get("frontend-deployment.yaml"));
        assertTrue(frontend.getEnv().stream().anyMatch(e -> "BACKEND_URL".equals(e.getName())
                && "http://fullstack-app-deploy-render-backend-service:8080".equals(e.getValue())));
    }

    @Test
    void eachComponentGetsAnAutoscalerAndTheIngressGoesThroughTheInterface() {
        Map<String, String> manifests = generator.generateApplication(new KubernetesApplicationRequest(
                "sd-test-demo",
                new KubernetesComponentRequest("demo-frontend", "registry/frontend@sha256:aaa", 2, 8080),
                new KubernetesComponentRequest("demo-backend", "registry/backend@sha256:bbb", 2, 8080),
                null,
                new KubernetesIngressRequest("test-demo.192.168.85.129.nip.io", null, null)
        )).manifests();

        String hpa = manifests.get("backend-hpa.yaml");
        assertNotNull(hpa);
        assertTrue(hpa.contains("kind: HorizontalPodAutoscaler"));
        assertTrue(hpa.contains("minReplicas: 2"));
        assertTrue(hpa.contains("maxReplicas: 6"));
        assertNotNull(manifests.get("frontend-hpa.yaml"));

        String ingress = manifests.get("application-ingress.yaml");
        assertFalse(ingress.contains("/api"), "l'interface relaie /api elle-même");
        assertTrue(ingress.contains("demo-frontend-service"));
        assertTrue(new KubernetesManifestValidator().validate(manifests).isEmpty(), "les autoscalers sont acceptés");
    }

    @Test
    void theAutoscalerStaysWithinTheMachineSize() {
        assertEquals(3, KubernetesManifestGeneratorService.maxReplicas(1));
        assertEquals(6, KubernetesManifestGeneratorService.maxReplicas(2));
        assertEquals(6, KubernetesManifestGeneratorService.maxReplicas(4));
    }

    @Test
    void aCanaryKeepsOnlyOnePodCopyThatTheStableServiceStillReaches() {
        Map<String, String> full = generator.generateApplication(new KubernetesApplicationRequest(
                "sd-dev-demo",
                new KubernetesComponentRequest("demo-frontend", "registry/frontend@sha256:aaa", 2, 8080),
                new KubernetesComponentRequest("demo-backend", "registry/backend@sha256:bbb", 2, 8080),
                null,
                null
        )).manifests();
        Map<String, String> canary = generator.canaryManifests(full);

        assertEquals(java.util.Set.of("backend-canary-deployment.yaml", "frontend-canary-deployment.yaml"), canary.keySet(),
                "ni Service, ni Ingress, ni autoscaler, ni base : la version stable reste intacte");
        io.fabric8.kubernetes.api.model.apps.Deployment d = io.fabric8.kubernetes.client.utils.Serialization.unmarshal(
                canary.get("backend-canary-deployment.yaml"), io.fabric8.kubernetes.api.model.apps.Deployment.class);
        assertEquals("demo-backend-canary", d.getMetadata().getName());
        assertEquals(1, d.getSpec().getReplicas());
        assertEquals("demo-backend", d.getSpec().getTemplate().getMetadata().getLabels().get("app"), "le Service stable le sélectionne");
        assertEquals("canary", d.getSpec().getSelector().getMatchLabels().get("track"));
        assertTrue(new KubernetesManifestValidator().validate(canary).isEmpty());
    }
}
