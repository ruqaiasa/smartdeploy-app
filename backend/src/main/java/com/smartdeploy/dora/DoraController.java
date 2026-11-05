package com.smartdeploy.dora;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DoraController {

    private final DoraService doraService;

    public DoraController(DoraService doraService) {
        this.doraService = doraService;
    }

    /** Les quatre indicateurs DORA sur une période, éventuellement filtrés. */
    @GetMapping("/api/dora")
    @PreAuthorize("isAuthenticated()")
    public DoraReport report(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(defaultValue = "false") boolean excludeBench
    ) {
        return doraService.compute(days, applicationId, environmentId, excludeBench);
    }
}
