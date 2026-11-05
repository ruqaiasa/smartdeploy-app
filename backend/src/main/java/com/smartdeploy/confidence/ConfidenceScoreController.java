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

    /** Score de confiance d'un déploiement à venir (version sur un environnement). */
    @GetMapping("/api/confidence")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConfidenceScore> score(@RequestParam Long versionId, @RequestParam Long environmentId) {
        return scoreService.compute(versionId, environmentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
