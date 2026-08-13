package com.smartdeploy.version;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/versions")
public class VersionController {

    private final VersionService versionService;

    public VersionController(
            VersionService versionService
    ) {
        this.versionService = versionService;
    }

    /* ==========================================================
     *                  CREATE VERSION
     * ==========================================================
     */

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VersionResponse create(
            @Valid @RequestBody
            CreateVersionRequest request
    ) {
        return versionService.create(request);
    }

    /* ==========================================================
     *                  GET VERSION
     * ==========================================================
     */

    @GetMapping("/{id}")
    public VersionResponse findById(
            @PathVariable Long id
    ) {
        return versionService.findById(id);
    }

    /* ==========================================================
     *                  GET ALL
     * ==========================================================
     */

    /* ==========================================================
     *              SEARCH AND PAGINATE VERSIONS
     * ==========================================================
     */

    @GetMapping
    public VersionPageResponse searchVersions(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            BuildStatus buildStatus,

            @RequestParam(required = false)
            Long applicationId

    ) {
        return versionService.searchVersions(
                page,
                size,
                search,
                buildStatus,
                applicationId
        );
    }

    /* ==========================================================
     *          GET BY APPLICATION
     * ==========================================================
     */

    @GetMapping("/application/{applicationId}")
    public List<VersionResponse> findByApplication(
            @PathVariable Long applicationId
    ) {
        return versionService.findByApplication(
                applicationId
        );
    }

    /* ==========================================================
     *                  UPDATE VERSION
     * ==========================================================
     */

    @PutMapping("/{id}")
    public VersionResponse update(

            @PathVariable Long id,

            @Valid
            @RequestBody
            UpdateVersionRequest request

    ) {

        return versionService.update(
                id,
                request
        );
    }

    /* ==========================================================
     *              UPDATE BUILD STATUS
     * ==========================================================
     */

    @PatchMapping("/{id}/status")
    public VersionResponse updateBuildStatus(

            @PathVariable Long id,

            @Valid
            @RequestBody
            UpdateBuildStatusRequest request

    ) {

        return versionService.updateBuildStatus(
                id,
                request.buildStatus()
        );
    }

    /* ==========================================================
     *              UPDATE BUILD METADATA
     * ==========================================================
     */

    @PatchMapping("/{id}/metadata")
    public VersionResponse updateBuildMetadata(

            @PathVariable Long id,

            @Valid
            @RequestBody
            UpdateBuildMetadataRequest request

    ) {

        return versionService.updateBuildMetadata(

                id,

                request.buildNumber(),

                request.dockerImage(),

                request.artifactPath(),

                request.buildLogsUrl()

        );
    }

    /* ==========================================================
     *                  DELETE VERSION
     * ==========================================================
     */

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(
            @PathVariable Long id
    ) {
        versionService.softDelete(id);
    }

    /* ==========================================================
     *                  RESTORE VERSION
     * ==========================================================
     */

    @PatchMapping("/{id}/restore")
    public VersionResponse restore(
            @PathVariable Long id
    ) {
        return versionService.restore(id);
    }

}