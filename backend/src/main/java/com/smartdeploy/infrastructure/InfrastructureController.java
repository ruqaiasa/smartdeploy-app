package com.smartdeploy.infrastructure;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class InfrastructureController {

    public record StorageHealthResponse(boolean prometheusReachable, List<RaidSnapshot> raids, List<FilesystemUsage> filesystems) {
    }

    private final InfrastructureHealthService service;

    public InfrastructureController(InfrastructureHealthService service) {
        this.service = service;
    }

    /** État des RAID de la machine du cluster (dernière vérification, toutes les 30 s). */
    @GetMapping("/api/infrastructure/storage")
    @PreAuthorize("isAuthenticated()")
    public StorageHealthResponse storage() {
        return new StorageHealthResponse(service.prometheusReachable(), service.storage(), service.filesystems());
    }
}
