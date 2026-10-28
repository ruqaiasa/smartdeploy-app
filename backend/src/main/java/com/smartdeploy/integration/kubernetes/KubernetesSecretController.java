package com.smartdeploy.integration.kubernetes;

import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kubernetes/secrets")
@PreAuthorize("hasAnyRole('ADMIN','DEVOPS')")
public class KubernetesSecretController {
    private final KubernetesClient kubernetes;
    private final EnvironmentRepository environments;

    public KubernetesSecretController(KubernetesClient kubernetes, EnvironmentRepository environments) {
        this.kubernetes = kubernetes;
        this.environments = environments;
    }

    public record SecretSummary(String name, List<String> keys) {}
    public record CreateSecretRequest(
            @NotNull Long environmentId,
            @NotBlank @Pattern(regexp="^[a-z0-9]([-a-z0-9]*[a-z0-9])?$", message="Nom Kubernetes invalide") String name,
            @NotBlank String key,
            @NotBlank String password
    ) {}

    private Environment environment(Long id) {
        Environment env = environments.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Environnement introuvable"));
        if (!env.isActive()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Environnement inactif");
        if (env.getNamespace() == null || env.getNamespace().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Namespace absent");
        return env;
    }

    @GetMapping
    public List<SecretSummary> list(@RequestParam Long environmentId) {
        String ns = environment(environmentId).getNamespace();
        return kubernetes.secrets().inNamespace(ns).list().getItems().stream()
                .filter(s -> s.getMetadata() != null && s.getMetadata().getName() != null)
                .map(s -> new SecretSummary(s.getMetadata().getName(),
                        s.getData() == null ? List.of() : s.getData().keySet().stream().sorted().toList()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<SecretSummary> create(@Valid @RequestBody CreateSecretRequest request) {
        String ns = environment(request.environmentId()).getNamespace();
        if (!request.key().matches("[-._a-zA-Z0-9]+") || request.key().length() > 253)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clé invalide");
        if (kubernetes.secrets().inNamespace(ns).withName(request.name()).get() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Secret déjà existant");
        // stringData est transmis à l'API Kubernetes via TLS; Kubernetes stocke ensuite data.
        // Ne jamais logger la requête, la réponse brute ou la valeur du mot de passe.
        Secret secret = new SecretBuilder()
                .withNewMetadata().withName(request.name()).withNamespace(ns).endMetadata()
                .withType("Opaque")
                .addToStringData(request.key(), request.password())
                .build();
        kubernetes.secrets().inNamespace(ns).resource(secret).create();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SecretSummary(request.name(), List.of(request.key())));
    }
}
