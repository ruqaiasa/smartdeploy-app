package com.smartdeploy.integration.kubernetes;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KubernetesNamespaceService {

    private final KubernetesClient kubernetes;

    public KubernetesNamespaceService(KubernetesClient kubernetes) {
        this.kubernetes = kubernetes;
    }

    /** Creates the namespace if needed; never changes an existing namespace. */
    public void ensureExists(String namespace) {
        if (namespace == null || namespace.length() > 63
                || !namespace.matches("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Namespace Kubernetes invalide");
        }

        try {
            if (kubernetes.namespaces().withName(namespace).get() != null) {
                return;
            }
            kubernetes.namespaces().resource(new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(namespace)
                    .addToLabels("app.kubernetes.io/managed-by", "smartdeploy")
                    .endMetadata()
                    .build()).create();
        } catch (KubernetesClientException e) {
            // Concurrent requests may create the same namespace. Only 409 is benign.
            if (e.getCode() == 409) {
                try {
                    if (kubernetes.namespaces().withName(namespace).get() != null) {
                        return;
                    }
                } catch (KubernetesClientException retryError) {
                    throw mapError("vérifier", namespace, retryError);
                }
            }
            throw mapError("créer", namespace, e);
        }
    }

    private ResponseStatusException mapError(
            String action, String namespace, KubernetesClientException cause) {
        String message = "Impossible de " + action + " le namespace '" + namespace + "'";
        if (cause.getCode() == 403 || cause.getCode() == 401) {
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    message + " : permissions Kubernetes insuffisantes pour le compte SmartDeploy", cause);
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                message + " : erreur Kubernetes HTTP " + cause.getCode(), cause);
    }
}
