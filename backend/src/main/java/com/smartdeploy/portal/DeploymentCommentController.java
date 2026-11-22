package com.smartdeploy.portal;

import com.smartdeploy.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/comments")
public class DeploymentCommentController {

    public record NewComment(String message) {
    }

    private final DeploymentCommentService service;

    public DeploymentCommentController(DeploymentCommentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DEVELOPER','DEVOPS','ADMIN')")
    public List<DeploymentCommentResponse> list(@PathVariable Long deploymentId, @AuthenticationPrincipal User user) {
        try {
            return service.list(deploymentId, user);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DEVELOPER','DEVOPS','ADMIN')")
    public DeploymentCommentResponse add(@PathVariable Long deploymentId, @RequestBody NewComment body,
                                         @AuthenticationPrincipal User user) {
        try {
            return service.add(deploymentId, user, body == null ? null : body.message());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
