package com.smartdeploy.portal;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Portail développeur : « Mon application est-elle prête pour SmartDeploy ? » */
@RestController
@RequestMapping("/api/portal/compatibility")
public class CompatibilityController {

    public record CompatibilityRequest(String repositoryUrl, String branch) {
    }

    private final RepositoryInspector inspector;

    public CompatibilityController(RepositoryInspector inspector) {
        this.inspector = inspector;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public CompatibilityReport check(@RequestBody CompatibilityRequest request) {
        try {
            return CompatibilityAnalyzer.analyze(inspector.inspect(request.repositoryUrl(), request.branch()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }
}
