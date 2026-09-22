package com.smartdeploy.alert;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<List<AlertResponse>> findAll(
            @RequestParam Long applicationId,
            @RequestParam Long environmentId
    ) {
        return ResponseEntity.ok(
                alertService.findAll(
                        applicationId,
                        environmentId
                )
        );
    }

    @GetMapping("/open")
    public ResponseEntity<List<AlertResponse>> findOpen(
            @RequestParam Long applicationId,
            @RequestParam Long environmentId
    ) {
        return ResponseEntity.ok(
                alertService.findOpen(
                        applicationId,
                        environmentId
                )
        );
    }
}