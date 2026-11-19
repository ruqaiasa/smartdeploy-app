package com.smartdeploy.backup;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BackupHealthController {

    private final BackupWatch watch;

    public BackupHealthController(BackupWatch watch) {
        this.watch = watch;
    }

    /** Disponibilité du stockage des sauvegardes (emplacement Velero « default »). */
    @GetMapping("/api/backups/storage-location")
    @PreAuthorize("isAuthenticated()")
    public BackupWatch.StorageLocationStatus storageLocation() {
        return watch.storageLocation();
    }
}
