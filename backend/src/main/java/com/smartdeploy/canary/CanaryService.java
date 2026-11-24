package com.smartdeploy.canary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdeploy.deployment.CreateDeploymentRequest;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentKubernetesConfigRepository;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentResponse;
import com.smartdeploy.deployment.DeploymentService;
import com.smartdeploy.deployment.DeploymentStateException;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.integration.kubernetes.KubernetesApplicationRequest;
import com.smartdeploy.user.User;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Déploiement progressif (canary) natif : un pod de la nouvelle version tourne à côté des pods de la version
 * stable, derrière le même Service, et reçoit une part du trafic proportionnelle (1 pod sur N+1). Le gardien
 * l'observe ; on le promeut (la version entière passe à la nouvelle version, images réutilisées) ou on
 * l'abandonne (le pod est supprimé). Le gardien l'abandonne lui-même s'il le juge dégradé.
 */
@Service
public class CanaryService {

    private static final Logger log = LoggerFactory.getLogger(CanaryService.class);
    static final String SUFFIX = "-canary";

    public record CanaryView(Long deploymentId, String state, boolean active, int stableReplicas, int trafficPercent,
                             LocalDateTime createdAt, LocalDateTime endedAt, String note) {
    }

    private final CanaryReleaseRepository canaries;
    private final DeploymentRepository deployments;
    private final DeploymentKubernetesConfigRepository configs;
    private final DeploymentService deploymentService;
    private final KubernetesClient kubernetes;
    private final ObjectMapper objectMapper;

    public CanaryService(CanaryReleaseRepository canaries, DeploymentRepository deployments,
                         DeploymentKubernetesConfigRepository configs, DeploymentService deploymentService,
                         KubernetesClient kubernetes, ObjectMapper objectMapper) {
        this.canaries = canaries;
        this.deployments = deployments;
        this.configs = configs;
        this.deploymentService = deploymentService;
        this.kubernetes = kubernetes;
        this.objectMapper = objectMapper;
    }

    /** Un canary suppose une version déjà en service, et pas d'autre canary en cours dans l'environnement. */
    @Transactional(readOnly = true)
    public void validate(Long environmentId) {
        boolean inService = deployments.findTop10ByEnvironment_IdAndDeletedFalseOrderByCreatedAtDesc(environmentId).stream()
                .anyMatch(d -> d.getStatus() == DeploymentStatus.SUCCESS);
        if (!inService) {
            throw new DeploymentStateException("Un déploiement progressif suppose une version déjà en service dans cet environnement");
        }
        boolean running = canaries.findByEnvironmentIdAndState(environmentId, CanaryRelease.State.OBSERVING).stream()
                .anyMatch(c -> deployments.findById(c.getDeploymentId())
                        .map(d -> d.getStatus() != DeploymentStatus.FAILED && d.getStatus() != DeploymentStatus.CANCELLED
                                && d.getStatus() != DeploymentStatus.REJECTED).orElse(false));
        if (running) {
            throw new DeploymentStateException("Un déploiement progressif est déjà en cours dans cet environnement : promouvez-le ou abandonnez-le d'abord");
        }
    }

    /** Enregistre le canary du déploiement qui vient d'être demandé. */
    @Transactional
    public void register(Long deploymentId) {
        Deployment d = deployments.findById(deploymentId).orElseThrow();
        JsonNode config = config(deploymentId);
        List<String> components = new ArrayList<>();
        int stable = 1;
        for (String key : List.of("backend", "frontend")) {
            JsonNode c = config.path(key);
            if (c.hasNonNull("name")) {
                components.add(c.path("name").asText());
                if (key.equals("backend")) {
                    stable = Math.max(1, c.path("replicas").asInt(1));
                }
            }
        }
        CanaryRelease c = new CanaryRelease();
        c.setDeploymentId(deploymentId);
        c.setEnvironmentId(d.getEnvironment().getId());
        c.setNamespace(d.getEnvironment().getNamespace());
        c.setComponents(String.join(",", components));
        c.setStableReplicas(stable);
        c.setState(CanaryRelease.State.OBSERVING);
        c.setCreatedAt(LocalDateTime.now());
        canaries.save(c);
    }

    @Transactional(readOnly = true)
    public Optional<CanaryView> view(Long deploymentId) {
        return canaries.findByDeploymentId(deploymentId).map(this::toView);
    }

    @Transactional(readOnly = true)
    public List<Long> activeDeploymentIds() {
        return canaries.findByState(CanaryRelease.State.OBSERVING).stream().map(this::toView)
                .filter(CanaryView::active).map(CanaryView::deploymentId).toList();
    }

    public boolean isObserving(Long deploymentId) {
        return canaries.findByDeploymentId(deploymentId).map(c -> c.getState() == CanaryRelease.State.OBSERVING).orElse(false);
    }

    /** Promouvoir : le canary est retiré, et une demande normale de la même version est créée (images réutilisées). */
    @Transactional
    public DeploymentResponse promote(Long deploymentId, User user) {
        CanaryRelease c = observing(deploymentId);
        Deployment d = deployments.findById(deploymentId).orElseThrow();
        if (d.getStatus() != DeploymentStatus.SUCCESS) {
            throw new DeploymentStateException("Seul un canary en service peut être promu");
        }
        KubernetesApplicationRequest config;
        try {
            config = objectMapper.treeToValue(config(deploymentId), KubernetesApplicationRequest.class);
        } catch (Exception exception) {
            throw new DeploymentStateException("Configuration du canary illisible : " + exception.getMessage());
        }
        removePods(c);
        c.setState(CanaryRelease.State.PROMOTED);
        c.setEndedAt(LocalDateTime.now());
        c.setNote("Promu par " + (user == null ? "SmartDeploy" : user.getEmail()));
        return deploymentService.create(new CreateDeploymentRequest(d.getVersion().getId(), d.getEnvironment().getId(), config,
                true, false, null, "Promotion du déploiement progressif #" + deploymentId), user);
    }

    /** Abandonner : le pod canary est supprimé, la version stable reste seule en service. */
    @Transactional
    public void abort(Long deploymentId, User user, String reason) {
        CanaryRelease c = observing(deploymentId);
        removePods(c);
        c.setState(CanaryRelease.State.ABORTED);
        c.setEndedAt(LocalDateTime.now());
        String note = (reason == null || reason.isBlank() ? "Abandonné" : reason) + (user == null ? "" : " (par " + user.getEmail() + ")");
        c.setNote(note);
        deployments.findById(deploymentId).ifPresent(d -> {
            if (d.getStatus() == DeploymentStatus.SUCCESS) {
                d.setStatus(DeploymentStatus.ROLLED_BACK);
                d.setErrorMessage("Déploiement progressif abandonné : " + note);
            }
        });
    }

    private CanaryRelease observing(Long deploymentId) {
        CanaryRelease c = canaries.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new NoSuchElementException("Ce déploiement n'est pas un déploiement progressif"));
        if (c.getState() != CanaryRelease.State.OBSERVING) {
            throw new DeploymentStateException("Ce déploiement progressif est déjà terminé");
        }
        return c;
    }

    private void removePods(CanaryRelease c) {
        for (String component : c.getComponents().split(",")) {
            if (component.isBlank()) {
                continue;
            }
            try {
                kubernetes.apps().deployments().inNamespace(c.getNamespace()).withName(component + SUFFIX).delete();
            } catch (RuntimeException exception) {
                log.warn("Canary : suppression de {}{} impossible dans {} ({})", component, SUFFIX, c.getNamespace(), exception.getMessage());
            }
        }
    }

    private JsonNode config(Long deploymentId) {
        return configs.findByDeploymentId(deploymentId).map(k -> {
            try {
                return objectMapper.readTree(k.getConfigurationJson());
            } catch (Exception exception) {
                return objectMapper.createObjectNode();
            }
        }).orElse(objectMapper.createObjectNode());
    }

    private CanaryView toView(CanaryRelease c) {
        DeploymentStatus status = deployments.findById(c.getDeploymentId()).map(Deployment::getStatus).orElse(null);
        boolean active = c.getState() == CanaryRelease.State.OBSERVING && status == DeploymentStatus.SUCCESS;
        return new CanaryView(c.getDeploymentId(), c.getState().name(), active, c.getStableReplicas(),
                trafficPercent(c.getStableReplicas()), c.getCreatedAt(), c.getEndedAt(), c.getNote());
    }

    /** Part du trafic reçue par le canary : 1 pod sur (pods stables + 1). */
    static int trafficPercent(int stableReplicas) {
        return (int) Math.round(100.0 / (Math.max(1, stableReplicas) + 1));
    }
}
