package com.smartdeploy.deployment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeploymentPromotionController {

    private final DeploymentPromotionService promotionService;

    public DeploymentPromotionController(DeploymentPromotionService promotionService) {
        this.promotionService = promotionService;
    }

    /** Vers quel environnement ce déploiement validé peut-il monter, et avec quelle configuration ? */
    @GetMapping("/api/deployments/{id}/promotion")
    @PreAuthorize("hasAnyRole('DEVOPS', 'ADMIN')")
    public PromotionProposal promotion(@PathVariable Long id) {
        return promotionService.propose(id);
    }
}
