package com.smartdeploy.audit;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Journal d'activité, consultable par les administrateurs.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final AuditIntegrityService auditIntegrityService;

    public AuditLogController(AuditLogService auditLogService, AuditIntegrityService auditIntegrityService) {
        this.auditLogService = auditLogService;
        this.auditIntegrityService = auditIntegrityService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AuditLogPageResponse search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<AuditAction> actions,
            @RequestParam(required = false) String search
    ) {
        return auditLogService.search(page, size, actions, search);
    }

    /** Vérifie la chaîne d'empreintes : toute modification ou suppression directe est détectée. */
    @GetMapping("/integrity")
    @PreAuthorize("hasRole('ADMIN')")
    public AuditIntegrityReport verifyIntegrity() {
        return auditIntegrityService.verify();
    }
}
