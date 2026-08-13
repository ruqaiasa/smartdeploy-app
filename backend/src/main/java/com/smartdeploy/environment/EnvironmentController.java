package com.smartdeploy.environment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    /* ==========================================================
     *                      DEPENDENCIES
     * ==========================================================
     */

    private final EnvironmentService environmentService;

    /* ==========================================================
     *                      CONSTRUCTOR
     * ==========================================================
     */

    public EnvironmentController(
            EnvironmentService environmentService
    ) {
        this.environmentService = environmentService;
    }

    /* ==========================================================
     *                  CREATE ENVIRONMENT
     * ==========================================================
     */

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse create(
            @Valid @RequestBody CreateEnvironmentRequest request
    ) {
        return environmentService.create(request);
    }

    /* ==========================================================
     *                  GET ENVIRONMENT BY ID
     * ==========================================================
     */

    @GetMapping("/{id}")
    public EnvironmentResponse findById(
            @PathVariable Long id
    ) {
        return environmentService.findById(id);
    }

    /* ==========================================================
     *                  GET ALL ENVIRONMENTS
     * ==========================================================
     */

    @GetMapping
    public EnvironmentPageResponse searchEnvironments(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            EnvironmentType type,

            @RequestParam(required = false)
            Boolean active,

            @RequestParam(required = false)
            Long applicationId
    ) {
        return environmentService.searchEnvironments(
                page,
                size,
                search,
                type,
                active,
                applicationId
        );
    }
    /* ==========================================================
     *                  UPDATE ENVIRONMENT
     * ==========================================================
     */

    @PutMapping("/{id}")
    public EnvironmentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEnvironmentRequest request
    ) {
        return environmentService.update(id, request);
    }

    /* ==========================================================
     *                  ACTIVATE ENVIRONMENT
     * ==========================================================
     */

    @PatchMapping("/{id}/activate")
    public EnvironmentResponse activate(
            @PathVariable Long id
    ) {
        return environmentService.activate(id);
    }

    /* ==========================================================
     *                  DEACTIVATE ENVIRONMENT
     * ==========================================================
     */

    @PatchMapping("/{id}/deactivate")
    public EnvironmentResponse deactivate(
            @PathVariable Long id
    ) {
        return environmentService.deactivate(id);
    }

    /* ==========================================================
     *                  DELETE ENVIRONMENT
     * ==========================================================
     */

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(
            @PathVariable Long id
    ) {
        environmentService.softDelete(id);
    }

    /* ==========================================================
     *                  RESTORE ENVIRONMENT
     * ==========================================================
     */

    @PatchMapping("/{id}/restore")
    public EnvironmentResponse restore(
            @PathVariable Long id
    ) {
        return environmentService.restore(id);
    }
}