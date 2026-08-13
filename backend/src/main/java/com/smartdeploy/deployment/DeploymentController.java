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

    public DeploymentController(
            DeploymentService deploymentService
    ) {
        this.deploymentService = deploymentService;
    }

    /* ==========================================================
     *                  CREATE DEPLOYMENT
     * ==========================================================
     */

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'DEVOPS', 'DEVELOPER')"
    )
    public DeploymentResponse create(
            @Valid @RequestBody CreateDeploymentRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return deploymentService.create(
                request,
                currentUser
        );
    }

    /* ==========================================================
     *                  GET DEPLOYMENT
     * ==========================================================
     */

    @GetMapping("/{id}")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'DEVOPS', 'DEVELOPER', 'VIEWER')"
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
            "hasAnyRole('ADMIN', 'DEVOPS', 'DEVELOPER', 'VIEWER')"
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
            Long environmentId
    ) {
        return deploymentService.searchDeployments(
                page,
                size,
                search,
                status,
                applicationId,
                versionId,
                environmentId
        );
    }

    /* ==========================================================
     *                  UPDATE STATUS
     * ==========================================================
     */

    @PatchMapping("/{id}/status")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'DEVOPS')"
    )
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
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'DEVOPS')"
    )
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

    @PostMapping("/{id}/rollback")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'DEVOPS')"
    )
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