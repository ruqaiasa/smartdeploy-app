package com.smartdeploy.guard;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class DeploymentGuardController {

    private final DeploymentGuardService guardService;

    public DeploymentGuardController(DeploymentGuardService guardService) {
        this.guardService = guardService;
    }

    /** Gardien du déploiement : statut, verdict, preuves et cible proposée. */
    @GetMapping("/api/deployments/{deploymentId}/guard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeploymentGuardResponse> forDeployment(@PathVariable Long deploymentId) {
        return guardService.findForDeployment(deploymentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Gardiens de plusieurs déploiements (page des déploiements), ou les plus récents. */
    @GetMapping("/api/deployment-guards")
    @PreAuthorize("isAuthenticated()")
    public List<DeploymentGuardResponse> list(
            @RequestParam(required = false) List<Long> deploymentIds,
            @RequestParam(defaultValue = "50") int limit
    ) {
        if (deploymentIds != null && !deploymentIds.isEmpty()) {
            return guardService.findForDeployments(deploymentIds.stream().limit(200).toList());
        }
        return guardService.recent(limit);
    }
}
