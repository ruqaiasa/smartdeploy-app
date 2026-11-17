package com.smartdeploy.deployment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeploymentPrecheckController {

    private final DeploymentPrecheckService precheckService;

    public DeploymentPrecheckController(DeploymentPrecheckService precheckService) {
        this.precheckService = precheckService;
    }

    /** Ce qu'il faut savoir avant de déployer cette version sur cet environnement. */
    @GetMapping("/api/deployments/precheck")
    @PreAuthorize("isAuthenticated()")
    public DeploymentPrecheck precheck(@RequestParam Long versionId, @RequestParam Long environmentId) {
        return precheckService.check(versionId, environmentId);
    }
}
