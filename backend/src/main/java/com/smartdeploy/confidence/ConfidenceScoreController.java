package com.smartdeploy.confidence;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfidenceScoreController {

    private final ConfidenceScoreService scoreService;

    public ConfidenceScoreController(ConfidenceScoreService scoreService) {
        this.scoreService = scoreService;
    }

    private com.smartdeploy.deployment.DeploymentReadAccess readAccess;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setReadAccess(com.smartdeploy.deployment.DeploymentReadAccess readAccess) {
        this.readAccess = readAccess;
    }


    /** Score de confiance d'un déploiement à venir (version sur un environnement). */
    @GetMapping("/api/confidence")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConfidenceScore> score(@RequestParam Long versionId, @RequestParam Long environmentId) {
        if (readAccess != null) {
            readAccess.ensureCanReadVersion(versionId);
        }
        return scoreService.compute(versionId, environmentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
