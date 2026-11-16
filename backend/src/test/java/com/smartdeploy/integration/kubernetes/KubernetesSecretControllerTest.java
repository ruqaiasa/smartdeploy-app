package com.smartdeploy.integration.kubernetes;

import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cas réel trouvé par le test de bout en bout : le premier déploiement d'un environnement
 * neuf échouait, car le Secret était créé avant l'espace de noms (404 « namespace not found »).
 */
class KubernetesSecretControllerTest {

    private static final String NS = "sd-test-fullstack-demo";
    private static final String NAME = "fullstack-demo-db-secret";

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateTheNamespaceBeforeTheSecret() {
        // La chaîne client.secrets().inNamespace(ns)…, simulée maillon par maillon
        // (les types génériques de fabric8 empêchent les « deep stubs » de Mockito).
        KubernetesClient client = mock(KubernetesClient.class);
        MixedOperation<Secret, SecretList, Resource<Secret>> secrets = mock(MixedOperation.class);
        NonNamespaceOperation<Secret, SecretList, Resource<Secret>> inNamespace = mock(NonNamespaceOperation.class);
        Resource<Secret> existing = mock(Resource.class);
        Resource<Secret> toCreate = mock(Resource.class);
        when(client.secrets()).thenReturn(secrets);
        when(secrets.inNamespace(NS)).thenReturn(inNamespace);
        when(inNamespace.withName(NAME)).thenReturn(existing);
        when(existing.get()).thenReturn(null);                       // le Secret n'existe pas encore
        when(inNamespace.resource(any(Secret.class))).thenReturn(toCreate);

        EnvironmentRepository environments = mock(EnvironmentRepository.class);
        Environment environment = mock(Environment.class);
        when(environment.isActive()).thenReturn(true);
        when(environment.getNamespace()).thenReturn(NS);
        when(environments.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(environment));
        KubernetesNamespaceService namespaces = mock(KubernetesNamespaceService.class);

        var response = new KubernetesSecretController(client, environments, namespaces).create(
                new KubernetesSecretController.CreateSecretRequest(7L, NAME, "password", "un-mot-de-passe"));

        assertEquals(201, response.getStatusCode().value());
        InOrder order = inOrder(namespaces, toCreate);
        order.verify(namespaces).ensureExists(NS);   // d'abord l'espace de noms…
        order.verify(toCreate).create();             // …ensuite le Secret
    }
}
