package com.smartdeploy.platformbackup;

import com.smartdeploy.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PlatformBackupController {

    private final PlatformBackupService service;

    public PlatformBackupController(PlatformBackupService service) {
        this.service = service;
    }

    @GetMapping("/api/platform-backup/status")
    @PreAuthorize("isAuthenticated()")
    public PlatformBackupService.Status status() {
        return service.status();
    }

    @PostMapping("/api/platform-backup/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> run(@AuthenticationPrincipal User currentUser) {
        try {
            return ResponseEntity.ok(service.run(currentUser == null ? null : currentUser.getEmail()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(Map.of("message", exception.getMessage()));
        }
    }
}
