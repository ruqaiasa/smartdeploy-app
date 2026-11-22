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

    private com.smartdeploy.deployment.DeploymentReadAccess readAccess;

    /** RG-O1 : un développeur ne lit que les déploiements de ses applications (facultatif dans les tests). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setReadAccess(com.smartdeploy.deployment.DeploymentReadAccess readAccess) {
        this.readAccess = readAccess;
    }

    private void checkRead(Long deploymentId) {
        if (readAccess != null) {
            readAccess.ensureCanRead(deploymentId);
        }
    }


    /** Vers quel environnement ce déploiement validé peut-il monter, et avec quelle configuration ? */
    @GetMapping("/api/deployments/{id}/promotion")
    // Le développeur peut demander la promotion de ses propres déploiements (soumise à l'accord habituel).
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
    public PromotionProposal promotion(@PathVariable Long id) {
        checkRead(id);
        return promotionService.propose(id);
    }
}
