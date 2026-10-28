package com.smartdeploy.application;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    /* ==========================================================
     *                      DEPENDENCIES
     * ==========================================================
     */

    private final ApplicationService applicationService;

    public ApplicationController(
            ApplicationService applicationService
    ) {
        this.applicationService = applicationService;
    }

    /* ==========================================================
     *                  CREATE APPLICATION
     * ==========================================================
     */

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
    public ApplicationResponse create(
            @Valid @RequestBody
            CreateApplicationRequest request
    ) {
        return applicationService.create(request);
    }

    /* ==========================================================
     *                  GET APPLICATION BY ID
     * ==========================================================
     */

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    public ApplicationResponse findById(
            @PathVariable Long id
    ) {
        return applicationService.findById(id);
    }

    /* ==========================================================
     *          SEARCH AND PAGINATE APPLICATIONS
     * ==========================================================
     */

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    public ApplicationPageResponse searchApplications(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            Technology technology,

            @RequestParam(required = false)
            Boolean active
    ) {
        return applicationService.searchApplications(
                page,
                size,
                search,
                technology,
                active
        );
    }

    /* ==========================================================
     *                  UPDATE APPLICATION
     * ==========================================================
     */

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
    public ApplicationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody
            UpdateApplicationRequest request
    ) {
        return applicationService.update(
                id,
                request
        );
    }

    /* ==========================================================
     *                  ACTIVATE APPLICATION
     * ==========================================================
     */

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
    public ApplicationResponse activate(
            @PathVariable Long id
    ) {
        return applicationService.activate(id);
    }

    /* ==========================================================
     *                  DEACTIVATE APPLICATION
     * ==========================================================
     */

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
    public ApplicationResponse deactivate(
            @PathVariable Long id
    ) {
        return applicationService.deactivate(id);
    }

    /* ==========================================================
     *                  DELETE APPLICATION
     * ==========================================================
     */

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
    public void softDelete(
            @PathVariable Long id
    ) {
        applicationService.softDelete(id);
    }

    /* ==========================================================
     *                  RESTORE APPLICATION
     * ==========================================================
     */

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
    public ApplicationResponse restore(
            @PathVariable Long id
    ) {
        return applicationService.restore(id);
    }

    /* ==========================================================
     *                  APPLICATION DETAILS
     * ==========================================================
     */

    @GetMapping("/{id}/details")
    @PreAuthorize("hasAnyRole('VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN')")
    public ApplicationDetailsResponse findDetailsById(
            @PathVariable Long id
    ) {
        return applicationService.findDetailsById(id);
    }
}