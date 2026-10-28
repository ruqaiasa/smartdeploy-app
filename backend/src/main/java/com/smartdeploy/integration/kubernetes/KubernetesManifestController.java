package com.smartdeploy.integration.kubernetes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.smartdeploy.deployment.DeploymentKubernetesConfig;
import com.smartdeploy.deployment.DeploymentKubernetesConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/kubernetes/manifests")
@RequiredArgsConstructor
public class KubernetesManifestController {

    private final KubernetesManifestGeneratorService generatorService;

    private final KubernetesManifestValidator manifestValidator;

    private final DeploymentKubernetesConfigRepository kubernetesConfigRepository;

    private final ObjectMapper objectMapper;
    @PostMapping("/preview")
    public ResponseEntity<KubernetesManifestPreviewResponse> preview(
            @Valid @RequestBody KubernetesDeploymentConfigRequest request
    ) {

        String deploymentYaml = generatorService.generateDeployment(request);
        String serviceYaml = generatorService.generateService(request);

        return ResponseEntity.ok(
                new KubernetesManifestPreviewResponse(
                        deploymentYaml,
                        serviceYaml
                )
        );
    }

    @PostMapping("/fullstack/preview")
    public ResponseEntity<KubernetesFullStackPreviewResponse> previewFullStack(
            @Valid @RequestBody KubernetesFullStackRequest request
    ) {
        return ResponseEntity.ok(
                generatorService.generateFullStack(request)
        );
    }
    @PostMapping("/database/preview")
    public ResponseEntity<KubernetesDatabasePreviewResponse> previewDatabase(
            @RequestParam String namespace,
            @Valid @RequestBody KubernetesDatabaseRequest request
    ) {
        return ResponseEntity.ok(
                generatorService.generateDatabase(namespace, request)
        );
    }
    @PostMapping("/application/preview")
    public ResponseEntity<KubernetesApplicationPreviewResponse> previewApplication(
            @Valid @RequestBody KubernetesApplicationRequest request
    ) {
        return ResponseEntity.ok(
                generatorService.generateApplication(request)
        );
    }
    @PostMapping("/application/validate")
    public ResponseEntity<Map<String, Object>> validateApplication(
            @Valid @RequestBody KubernetesApplicationRequest request
    ) {

        KubernetesApplicationPreviewResponse preview =
                generatorService.generateApplication(request);

        var errors = manifestValidator.validate(preview.manifests());

        return ResponseEntity.ok(
                Map.of(
                        "valid", errors.isEmpty(),
                        "manifestCount", preview.manifests().size(),
                        "errors", errors
                )
        );
    }
    @PostMapping("/application/export")
    public ResponseEntity<byte[]> exportApplication(
            @Valid @RequestBody KubernetesApplicationRequest request
    ) throws IOException {

        KubernetesApplicationPreviewResponse preview =
                generatorService.generateApplication(request);

        var errors = manifestValidator.validate(preview.manifests());

        if (!errors.isEmpty()) {
            return ResponseEntity.unprocessableEntity().build();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(output)) {

            for (Map.Entry<String, String> entry : preview.manifests().entrySet()) {

                zip.putNextEntry(new ZipEntry(entry.getKey()));

                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));

                zip.closeEntry();
            }
        }

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"smartdeploy-kubernetes.zip\""
                )
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(output.toByteArray());
    }
    @GetMapping("/deployments/{deploymentId}/export")
    public ResponseEntity<byte[]> exportDeploymentManifests(
            @PathVariable Long deploymentId,
            @RequestParam String backendImage,
            @RequestParam(required = false) String frontendImage
    ) throws IOException {

        // 1. RÃ©cupÃ©rer la configuration enregistrÃ©e dans PostgreSQL
        DeploymentKubernetesConfig savedConfig =
                kubernetesConfigRepository
                        .findByDeploymentId(deploymentId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Configuration Kubernetes introuvable"
                                )
                        );

        // 2. Reconstruire la requÃªte Kubernetes
        KubernetesApplicationRequest request;

        try {
            JsonNode parsed = objectMapper.readTree(savedConfig.getConfigurationJson());
            if (!(parsed instanceof ObjectNode root)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Configuration Kubernetes invalide");
            }
            if (backendImage == null || backendImage.isBlank() ||
                    !backendImage.matches("^[a-zA-Z0-9][a-zA-Z0-9._:/@-]{0,254}$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Image backend obligatoire et valide");
            }
            JsonNode backend = root.path("backend");
            if (!(backend instanceof ObjectNode backendObject)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Composant backend introuvable");
            }
            backendObject.put("image", backendImage);

            JsonNode frontend = root.path("frontend");
            if (frontend.isObject()) {
                if (frontendImage == null || frontendImage.isBlank() ||
                        !frontendImage.matches("^[a-zA-Z0-9][a-zA-Z0-9._:/@-]{0,254}$")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Image frontend obligatoire en mode Full Stack");
                }
                ((ObjectNode) frontend).put("image", frontendImage);
            }
            request = objectMapper.treeToValue(root, KubernetesApplicationRequest.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Configuration Kubernetes JSON invalide",
                    e
            );
        }

        // 3. GÃ©nÃ©rer les manifests
        KubernetesApplicationPreviewResponse preview =
                generatorService.generateApplication(request);

        // 4. Valider les manifests
        var errors = manifestValidator.validate(preview.manifests());

        if (!errors.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Manifests Kubernetes invalides : " + errors
            );
        }

        // 5. CrÃ©er l'archive ZIP
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(output)) {

            for (Map.Entry<String, String> entry :
                    preview.manifests().entrySet()) {

                zip.putNextEntry(
                        new ZipEntry(entry.getKey())
                );

                zip.write(
                        entry.getValue()
                                .getBytes(StandardCharsets.UTF_8)
                );

                zip.closeEntry();
            }
        }

        // 6. Retourner le ZIP Ã  Jenkins
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"deployment-"
                                + deploymentId
                                + "-manifests.zip\""
                )
                .contentType(
                        MediaType.parseMediaType("application/zip")
                )
                .body(output.toByteArray());
    }
}
