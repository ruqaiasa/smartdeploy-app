package com.smartdeploy.canary;

import com.smartdeploy.deployment.DeploymentReadAccess;
import com.smartdeploy.deployment.DeploymentResponse;
import com.smartdeploy.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
public class CanaryController {

    public record AbortRequest(String reason) {
    }

    private final CanaryService canaries;
    private final DeploymentReadAccess readAccess;

    public CanaryController(CanaryService canaries, DeploymentReadAccess readAccess) {
        this.canaries = canaries;
        this.readAccess = readAccess;
    }

    @GetMapping("/api/deployments/{id}/canary")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    public CanaryService.CanaryView view(@PathVariable Long id) {
        readAccess.ensureCanRead(id);
        return canaries.view(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pas un déploiement progressif"));
    }

    @GetMapping("/api/deployments/canaries")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    public List<Long> active() {
        return canaries.activeDeploymentIds();
    }

    @PostMapping("/api/deployments/{id}/canary/promote")
    @PreAuthorize("hasAnyRole('DEVOPS', 'ADMIN')")
    public DeploymentResponse promote(@PathVariable Long id, @AuthenticationPrincipal User user) {
        try {
            return canaries.promote(id, user);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/api/deployments/{id}/canary/abort")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('DEVOPS', 'ADMIN')")
    public void abort(@PathVariable Long id, @RequestBody(required = false) AbortRequest body, @AuthenticationPrincipal User user) {
        try {
            canaries.abort(id, user, body == null ? null : body.reason());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
