package com.smartdeploy.integration.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class KubernetesManifestValidator {

    private static final Set<String> ALLOWED_KINDS = Set.of(
            "Deployment",
            "Service",
            "StatefulSet",
            "Ingress"
    );

    public List<String> validate(Map<String, String> manifests) {

        List<String> errors = new ArrayList<>();

        if (manifests == null || manifests.isEmpty()) {
            errors.add("Aucun manifest Kubernetes à valider");
            return errors;
        }

        for (Map.Entry<String, String> entry : manifests.entrySet()) {

            String filename = entry.getKey();
            String yaml = entry.getValue();

            if (yaml == null || yaml.isBlank()) {
                errors.add(filename + " : manifest vide");
                continue;
            }

            try {
                HasMetadata resource = Serialization.unmarshal(
                        new ByteArrayInputStream(
                                yaml.getBytes(StandardCharsets.UTF_8)
                        )
                );

                if (resource == null) {
                    errors.add(filename + " : ressource invalide");
                    continue;
                }

                if (!ALLOWED_KINDS.contains(resource.getKind())) {
                    errors.add(filename + " : type non autorisé");
                }

                if (resource.getMetadata() == null
                        || resource.getMetadata().getName() == null
                        || resource.getMetadata().getName().isBlank()) {
                    errors.add(filename + " : metadata.name manquant");
                }

                if (resource.getMetadata() == null
                        || resource.getMetadata().getNamespace() == null
                        || resource.getMetadata().getNamespace().isBlank()) {
                    errors.add(filename + " : namespace manquant");
                }

            } catch (Exception e) {
                errors.add(filename + " : YAML invalide");
            }
        }

        return errors;
    }
}