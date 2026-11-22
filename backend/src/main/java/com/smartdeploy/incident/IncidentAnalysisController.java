package com.smartdeploy.incident;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/analysis")
public class IncidentAnalysisController {

    private final IncidentAnalysisService analysisService;

    public IncidentAnalysisController(IncidentAnalysisService analysisService) {
        this.analysisService = analysisService;
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


    /** Dernier diagnostic du déploiement. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IncidentAnalysisResponse> latest(@PathVariable Long deploymentId) {
        checkRead(deploymentId);
        return analysisService.latest(deploymentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Relance l'analyse (par exemple après avoir complété les droits du cluster). */
    @PostMapping
    @PreAuthorize("hasAnyRole('DEVOPS','ADMIN')")
    public ResponseEntity<IncidentAnalysisResponse> rerun(@PathVariable Long deploymentId) {
        checkRead(deploymentId);
        return analysisService.analyze(deploymentId, IncidentTrigger.MANUAL)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
