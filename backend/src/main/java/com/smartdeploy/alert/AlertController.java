package com.smartdeploy.alert;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
public class AlertController {

    private final AlertService alertService;

    public AlertController(
            AlertService alertService
    ) {
        this.alertService = alertService;
    }

    /* ==========================================================
     *                  GET ALL ALERTS
     * ==========================================================
     */

    @GetMapping
    public ResponseEntity<List<AlertResponse>> findAll(
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long environmentId
    ) {

        if (applicationId == null && environmentId == null) {
            return ResponseEntity.ok(
                    alertService.findAll()
            );
        }

        validateFilters(
                applicationId,
                environmentId
        );

        return ResponseEntity.ok(
                alertService.findAll(
                        applicationId,
                        environmentId
                )
        );
    }

    /* ==========================================================
     *                  GET OPEN ALERTS
     * ==========================================================
     */

    @GetMapping("/open")
    public ResponseEntity<List<AlertResponse>> findOpen(
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long environmentId
    ) {

        if (applicationId == null && environmentId == null) {
            return ResponseEntity.ok(
                    alertService.findOpen()
            );
        }

        validateFilters(
                applicationId,
                environmentId
        );

        return ResponseEntity.ok(
                alertService.findOpen(
                        applicationId,
                        environmentId
                )
        );
    }

    /* ==========================================================
     *                  FILTER VALIDATION
     * ==========================================================
     */

    private void validateFilters(
            Long applicationId,
            Long environmentId
    ) {

        if (applicationId == null || environmentId == null) {
            throw new IllegalArgumentException(
                    "applicationId et environmentId doivent être fournis ensemble."
            );
        }
    }
}