package com.smartdeploy.deployment;

import com.smartdeploy.user.User;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final DeploymentPipelineLauncher pipelineLauncher;

    public DeploymentController(
            DeploymentService deploymentService,
            DeploymentPipelineLauncher pipelineLauncher
    ) {
        this.deploymentService = deploymentService;
        this.pipelineLauncher = pipelineLauncher;
    }

    /* ==========================================================
     *                  CREATE DEPLOYMENT
     * ==========================================================
     */

    /** Déploiement progressif (facultatif : absent des tests de la couche web). */
    private com.smartdeploy.canary.CanaryService canaryService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setCanaryService(com.smartdeploy.canary.CanaryService canaryService) {
        this.canaryService = canaryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // RG-D1 : un développeur peut demander un déploiement ; il attend alors l'accord d'un DevOps.
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
    public DeploymentResponse create(
            @Valid @RequestBody CreateDeploymentRequest request,
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "STANDARD") String strategy
    ) {
        boolean canary = "CANARY".equalsIgnoreCase(strategy) && canaryService != null;
        if (canary) {
            canaryService.validate(request.environmentId());
        }
        DeploymentResponse created = deploymentService.create(
                request,
                currentUser
        );
        if (canary) {
            canaryService.register(created.id());
        }

        // Relecture après commit : si Jenkins a refusé le déclenchement,
        // le déploiement est déjà FAILED et l'interface doit le voir.
        return deploymentService.findById(created.id());
    }

    /* ==========================================================
     *                  GET DEPLOYMENT
     * ==========================================================
     */

    @GetMapping("/{id}")
    @PreAuthorize(
            "hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')"
    )
    public DeploymentResponse findById(
            @PathVariable Long id
    ) {
        return deploymentService.findById(id);
    }

    /* ==========================================================
     *                  SEARCH DEPLOYMENTS
     * ==========================================================
     */

    @GetMapping
    @PreAuthorize(
            "hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')"
    )
    public DeploymentPageResponse searchDeployments(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            DeploymentStatus status,

            @RequestParam(required = false)
            Long applicationId,

            @RequestParam(required = false)
            Long versionId,

            @RequestParam(required = false)
            Long environmentId,

            // Portail développeur : « Mes demandes », les déploiements de l'utilisateur connecté
            @RequestParam(defaultValue = "false")
            boolean mine,

            @AuthenticationPrincipal User currentUser
    ) {
        return deploymentService.searchDeployments(
                page,
                size,
                search,
                status,
                applicationId,
                versionId,
                environmentId,
                mine && currentUser != null ? currentUser.getId() : null
        );
    }

    /* ==========================================================
     *                  UPDATE STATUS
     * ==========================================================
     */

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('DEVOPS', 'ADMIN')")
    public DeploymentResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody
            UpdateDeploymentStatusRequest request
    ) {
        return deploymentService.updateStatus(
                id,
                request
        );
    }

    /* ==========================================================
     *                  UPDATE METADATA
     * ==========================================================
     */

    @PatchMapping("/{id}/metadata")
    @PreAuthorize("hasAnyRole('DEVOPS', 'ADMIN')")
    public DeploymentResponse updateMetadata(
            @PathVariable Long id,
            @Valid @RequestBody
            UpdateDeploymentMetadataRequest request
    ) {
        return deploymentService.updateMetadata(
                id,
                request
        );
    }

    /* ==========================================================
     *                  CREATE ROLLBACK
     * ==========================================================
     */

    /**
     * Annule un déploiement en attente ou en cours : arrêt du build
     * Jenkins (ou retrait de la file d'attente), puis statut CANCELLED.
     */
    @PostMapping("/{id}/cancel")
    // RG-D3 : un développeur peut retirer sa propre demande en attente (vérifié par le service).
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
    public DeploymentResponse cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser
    ) {
        return pipelineLauncher.cancel(id, currentUser);
    }

    /** Corps des décisions d'approbation : un commentaire, obligatoire pour un refus. */
    public record ApprovalDecision(String comment) {
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('DEVOPS', 'ADMIN')")
    public DeploymentResponse approve(
            @PathVariable Long id,
            @RequestBody(required = false) ApprovalDecision decision,
            @AuthenticationPrincipal User currentUser
    ) {
        return deploymentService.approve(id, currentUser, decision == null ? null : decision.comment());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('DEVOPS', 'ADMIN')")
    public DeploymentResponse reject(
            @PathVariable Long id,
            @RequestBody(required = false) ApprovalDecision decision,
            @AuthenticationPrincipal User currentUser
    ) {
        return deploymentService.reject(id, currentUser, decision == null ? null : decision.comment());
    }

    /** Nombre de demandes d'approbation en attente (pastille du menu). */
    @GetMapping("/approvals/pending-count")
    @PreAuthorize("isAuthenticated()")
    public java.util.Map<String, Long> pendingApprovals() {
        return java.util.Map.of("count", deploymentService.pendingApprovals());
    }

    @PostMapping("/{id}/rollback")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DEVOPS', 'ADMIN')")
    public DeploymentResponse rollback(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser
    ) {
        return deploymentService.createRollback(
                id,
                currentUser
        );
    }

    /* ==========================================================
     *                  SOFT DELETE
     * ==========================================================
     */

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void softDelete(
            @PathVariable Long id
    ) {
        deploymentService.softDelete(id);
    }
}