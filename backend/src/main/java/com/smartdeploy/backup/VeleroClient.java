package com.smartdeploy.backup;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pilote Velero par l'API Kubernetes : une sauvegarde ou une restauration
 * est un objet (Backup, Restore) que Velero exécute. SmartDeploy n'a donc
 * besoin d'aucun accès à Azure ni de la clé du stockage.
 */
@Component
public class VeleroClient {

    static final ResourceDefinitionContext BACKUP = new ResourceDefinitionContext.Builder()
            .withGroup("velero.io").withVersion("v1").withKind("Backup").withPlural("backups").withNamespaced(true).build();
    static final ResourceDefinitionContext RESTORE = new ResourceDefinitionContext.Builder()
            .withGroup("velero.io").withVersion("v1").withKind("Restore").withPlural("restores").withNamespaced(true).build();

    private final KubernetesClient kubernetesClient;
    private final VeleroProperties properties;

    public VeleroClient(KubernetesClient kubernetesClient, VeleroProperties properties) {
        this.kubernetesClient = kubernetesClient;
        this.properties = properties;
    }

    public void createBackup(String name, String namespace, String ttl, Map<String, String> labels) {
        GenericKubernetesResource backup = new GenericKubernetesResourceBuilder()
                .withApiVersion("velero.io/v1").withKind("Backup")
                .withNewMetadata().withName(name).withNamespace(properties.namespace()).addToLabels(labels).endMetadata()
                .withAdditionalProperties(Map.of("spec", Map.of(
                        "includedNamespaces", List.of(namespace),
                        "ttl", ttl,
                        "storageLocation", "default",
                        "defaultVolumesToFsBackup", true)))
                .build();
        kubernetesClient.genericKubernetesResources(BACKUP).inNamespace(properties.namespace()).resource(backup).create();
    }

    public void createRestore(String name, String backupName, String sourceNamespace, String targetNamespace, Map<String, String> labels) {
        Map<String, Object> spec = new java.util.HashMap<>();
        spec.put("backupName", backupName);
        spec.put("includedNamespaces", List.of(sourceNamespace));
        if (!sourceNamespace.equals(targetNamespace)) {
            spec.put("namespaceMapping", Map.of(sourceNamespace, targetNamespace));
        }
        GenericKubernetesResource restore = new GenericKubernetesResourceBuilder()
                .withApiVersion("velero.io/v1").withKind("Restore")
                .withNewMetadata().withName(name).withNamespace(properties.namespace()).addToLabels(labels).endMetadata()
                .withAdditionalProperties(Map.of("spec", spec))
                .build();
        kubernetesClient.genericKubernetesResources(RESTORE).inNamespace(properties.namespace()).resource(restore).create();
    }

    public Optional<GenericKubernetesResource> backup(String name) {
        return Optional.ofNullable(kubernetesClient.genericKubernetesResources(BACKUP)
                .inNamespace(properties.namespace()).withName(name).get());
    }

    public Optional<GenericKubernetesResource> restore(String name) {
        return Optional.ofNullable(kubernetesClient.genericKubernetesResources(RESTORE)
                .inNamespace(properties.namespace()).withName(name).get());
    }

    public List<GenericKubernetesResource> backups() {
        return kubernetesClient.genericKubernetesResources(BACKUP).inNamespace(properties.namespace()).list().getItems();
    }

    public boolean namespaceExists(String namespace) {
        return kubernetesClient.namespaces().withName(namespace).get() != null;
    }

    public void deleteNamespace(String namespace) {
        kubernetesClient.namespaces().withName(namespace).delete();
    }

    /** Phase Velero d'un objet (New, InProgress, Completed, PartiallyFailed, Failed…). */
    static String phase(GenericKubernetesResource resource) {
        Object status = resource.getAdditionalProperties().get("status");
        if (status instanceof Map<?, ?> map && map.get("phase") != null) {
            return String.valueOf(map.get("phase"));
        }
        return "New";
    }

    static boolean finished(String phase) {
        return List.of("Completed", "PartiallyFailed", "Failed", "FailedValidation").contains(phase);
    }
}
