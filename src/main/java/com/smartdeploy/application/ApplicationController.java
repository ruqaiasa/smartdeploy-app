package com.smartdeploy.application;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ApplicationResponse findById(
            @PathVariable Long id
    ) {
        return applicationService.findById(id);
    }

    /* ==========================================================
     *                  GET ALL APPLICATIONS
     * ==========================================================
     */

    /* ==========================================================
     *          SEARCH AND PAGINATE APPLICATIONS
     * ==========================================================
     */

    @GetMapping
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
     *              ACTIVATE APPLICATION
     * ==========================================================
     */

    @PatchMapping("/{id}/activate")
    public ApplicationResponse activate(
            @PathVariable Long id
    ) {
        return applicationService.activate(id);
    }
    /* ==========================================================
     *              DEACTIVATE APPLICATION
     * ==========================================================
     */

    @PatchMapping("/{id}/deactivate")
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
    public ApplicationResponse restore(
            @PathVariable Long id
    ) {
        return applicationService.restore(id);
    }

    @GetMapping("/{id}/details")
    public ApplicationDetailsResponse findDetailsById(
            @PathVariable Long id
    ) {
        return applicationService.findDetailsById(id);
    }
}