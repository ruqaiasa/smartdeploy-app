package com.smartdeploy.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.environment.EnvironmentResponse;
@Service
@Transactional
public class ApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ApplicationRepository applicationRepository;
    private final EnvironmentRepository environmentRepository;
    public ApplicationService(
            ApplicationRepository applicationRepository,
            EnvironmentRepository environmentRepository
    ) {
        this.applicationRepository =
                applicationRepository;

        this.environmentRepository =
                environmentRepository;
    }

    /* ==========================================================
     *                  CREATE APPLICATION
     * ==========================================================
     */

    private ApplicationAccess access;
    private com.smartdeploy.user.UserRepository userRepository;

    /** RG-O1 : contrôle d'accès (facultatif, absent des tests unitaires). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAccess(ApplicationAccess access) {
        this.access = access;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setUserRepository(com.smartdeploy.user.UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** RG-O1 : le créateur est le propriétaire ; un administrateur ou un DevOps peut en désigner un autre. */
    private com.smartdeploy.user.User chosenOwner(Long ownerId) {
        com.smartdeploy.user.User current = access == null ? null : access.currentUser();
        if (ownerId != null && current != null && !ApplicationAccess.isRestricted(current) && userRepository != null) {
            return userRepository.findByIdAndDeletedFalse(ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("Propriétaire introuvable : " + ownerId));
        }
        return current;
    }

    public ApplicationResponse create(
            CreateApplicationRequest request
    ) {
        String normalizedName =
                normalizeRequiredText(
                        request.name()
                );

        checkNameAvailability(
                normalizedName,
                null
        );

        Application application =
                Application.builder()
                        .name(normalizedName)
                        .description(
                                normalizeNullableText(
                                        request.description()
                                )
                        )
                        .repositoryUrl(
                                normalizeRequiredText(
                                        request.repositoryUrl()
                                )
                        )
                        .technology(
                                request.technology()
                        )
                        .active(true)
                        .deleted(false)
                        .owner(chosenOwner(request.ownerId()))
                        .build();

        Application savedApplication =
                applicationRepository.saveAndFlush(
                        application
                );

        return toResponse(savedApplication);
    }

    /* ==========================================================
     *                  GET APPLICATION BY ID
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public ApplicationResponse findById(
            Long id
    ) {
        Application application =
                findExistingApplication(id);

        return toResponse(application);
    }

    /* ==========================================================
     *              SEARCH AND PAGINATE APPLICATIONS
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public ApplicationPageResponse searchApplications(
            int page,
            int size,
            String search,
            Technology technology,
            Boolean active
    ) {
        return searchApplications(page, size, search, technology, active, false);
    }

    public ApplicationPageResponse searchApplications(
            int page,
            int size,
            String search,
            Technology technology,
            Boolean active,
            boolean mineOnly
    ) {
        int normalizedPage =
                Math.max(page, 0);

        int normalizedSize =
                Math.min(
                        Math.max(size, 1),
                        MAX_PAGE_SIZE
                );

        Pageable pageable =
                PageRequest.of(
                        normalizedPage,
                        normalizedSize,
                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                );

        String normalizedSearch =
                normalizeNullableText(search);

        if (normalizedSearch == null) {
            normalizedSearch = "";
        }

        // RG-O1 : un développeur ne voit que ses applications ; « mine » limite aussi les autres rôles aux leurs.
        com.smartdeploy.user.User current = access == null ? null : access.currentUser();
        Long ownerId = access == null ? null : (mineOnly && current != null ? current.getId() : access.ownerFilter());
        Page<Application> applicationPage = ownerId != null
                ? applicationRepository.searchOwnedApplications(normalizedSearch, technology, active, ownerId, pageable)
                : applicationRepository
                        .searchApplications(
                                normalizedSearch,
                                technology,
                                active,
                                pageable
                        );

        return new ApplicationPageResponse(
                applicationPage
                        .getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),

                applicationPage.getNumber(),

                applicationPage.getSize(),

                applicationPage.getTotalElements(),

                applicationPage.getTotalPages(),

                applicationPage.isFirst(),

                applicationPage.isLast()
        );
    }

    /* ==========================================================
     *                  UPDATE APPLICATION
     * ==========================================================
     */

    public ApplicationResponse update(
            Long id,
            UpdateApplicationRequest request
    ) {
        Application application =
                findExistingApplication(id);

        String normalizedName =
                normalizeRequiredText(
                        request.name()
                );

        checkNameAvailability(
                normalizedName,
                application.getId()
        );

        application.setName(
                normalizedName
        );

        application.setDescription(
                normalizeNullableText(
                        request.description()
                )
        );

        application.setRepositoryUrl(
                normalizeRequiredText(
                        request.repositoryUrl()
                )
        );

        application.setTechnology(
                request.technology()
        );

        // RG-O1 : seul un administrateur ou un DevOps change le propriétaire.
        if (request.ownerId() != null && access != null && !ApplicationAccess.isRestricted(access.currentUser())) {
            application.setOwner(chosenOwner(request.ownerId()));
        }

        Application updatedApplication =
                applicationRepository.saveAndFlush(
                        application
                );

        return toResponse(updatedApplication);
    }

    /* ==========================================================
     *                  ACTIVATE APPLICATION
     * ==========================================================
     */

    public ApplicationResponse activate(
            Long id
    ) {
        Application application =
                findExistingApplication(id);

        application.setActive(true);

        Application activatedApplication =
                applicationRepository.saveAndFlush(
                        application
                );

        return toResponse(activatedApplication);
    }

    /* ==========================================================
     *                  DEACTIVATE APPLICATION
     * ==========================================================
     */

    public ApplicationResponse deactivate(
            Long id
    ) {
        Application application =
                findExistingApplication(id);

        application.setActive(false);

        Application deactivatedApplication =
                applicationRepository.saveAndFlush(
                        application
                );

        return toResponse(deactivatedApplication);
    }

    /* ==========================================================
     *                  SOFT DELETE APPLICATION
     * ==========================================================
     */

    public void softDelete(
            Long id
    ) {
        Application application =
                findExistingApplication(id);

        application.setDeleted(true);
        application.setActive(false);

        applicationRepository.saveAndFlush(
                application
        );
    }

    /* ==========================================================
     *                  RESTORE APPLICATION
     * ==========================================================
     */

    public ApplicationResponse restore(
            Long id
    ) {
        Application application =
                applicationRepository
                        .findByIdAndDeletedTrue(id)
                        .orElseThrow(() ->
                                new ApplicationNotFoundException(
                                        id
                                )
                        );

        checkNameAvailability(
                application.getName(),
                application.getId()
        );

        application.setDeleted(false);
        application.setActive(true);

        Application restoredApplication =
                applicationRepository.saveAndFlush(
                        application
                );

        return toResponse(restoredApplication);
    }

    /* ==========================================================
     *                  FIND APPLICATION
     * ==========================================================
     */

    private Application findExistingApplication(
            Long id
    ) {
        Application application = applicationRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() ->
                        new ApplicationNotFoundException(id)
                );
        if (access != null) {
            access.ensureCanAccess(application);
        }
        return application;
    }

    /* ==========================================================
     *                  NAME AVAILABILITY
     * ==========================================================
     */

    private void checkNameAvailability(
            String name,
            Long excludedApplicationId
    ) {
        boolean activeApplicationExists;
        boolean deletedApplicationExists;

        if (excludedApplicationId == null) {
            activeApplicationExists =
                    applicationRepository
                            .existsByNameIgnoreCaseAndDeletedFalse(
                                    name
                            );

            deletedApplicationExists =
                    applicationRepository
                            .existsByNameIgnoreCaseAndDeletedTrue(
                                    name
                            );
        } else {
            activeApplicationExists =
                    applicationRepository
                            .existsByNameIgnoreCaseAndDeletedFalseAndIdNot(
                                    name,
                                    excludedApplicationId
                            );

            deletedApplicationExists =
                    applicationRepository
                            .existsByNameIgnoreCaseAndDeletedTrueAndIdNot(
                                    name,
                                    excludedApplicationId
                            );
        }

        if (activeApplicationExists) {
            throw new ApplicationAlreadyExistsException(
                    "Une application active avec le nom '"
                            + name
                            + "' existe déjà."
            );
        }

        if (deletedApplicationExists) {
            throw new ApplicationAlreadyExistsException(
                    "Une application supprimée avec le nom '"
                            + name
                            + "' existe déjà. "
                            + "Veuillez la restaurer."
            );
        }
    }

    /* ==========================================================
     *                  TEXT NORMALIZATION
     * ==========================================================
     */

    private String normalizeRequiredText(
            String value
    ) {
        return value.trim();
    }

    private String normalizeNullableText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalizedValue =
                value.trim();

        return normalizedValue.isEmpty()
                ? null
                : normalizedValue;
    }

    /* ==========================================================
     *                  ENTITY TO RESPONSE
     * ==========================================================
     */

    private ApplicationResponse toResponse(
            Application application
    ) {
        return new ApplicationResponse(
                application.getId(),
                application.getName(),
                application.getDescription(),
                application.getRepositoryUrl(),
                application.getTechnology(),
                application.isActive(),
                application.isDeleted(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                application.getOwner() == null ? null : application.getOwner().getId(),
                application.getOwner() == null ? null : application.getOwner().getEmail()
        );
    }

    @Transactional(readOnly = true)
    public ApplicationDetailsResponse findDetailsById(
            Long id
    ) {
        Application application =
                applicationRepository
                        .findByIdAndDeletedFalse(id)
                        .orElseThrow(() ->
                                new ApplicationNotFoundException(id)
                        );

        var environments =
                environmentRepository
                        .findAllByApplicationIdAndDeletedFalseOrderByCreatedAtDesc(
                                id
                        )
                        .stream()
                        .map(this::toEnvironmentResponse)
                        .toList();

        return new ApplicationDetailsResponse(
                application.getId(),
                application.getName(),
                application.getDescription(),
                application.getRepositoryUrl(),
                application.getTechnology(),
                application.isActive(),
                application.isDeleted(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                environments
        );
    }
    private EnvironmentResponse toEnvironmentResponse(
            Environment environment
    ) {
        return new EnvironmentResponse(
                environment.getId(),

                environment
                        .getApplication()
                        .getId(),

                environment
                        .getApplication()
                        .getName(),

                environment.getType(),

                environment.getName(),

                environment.getDescription(),

                environment.getNamespace(),

                environment.getClusterUrl(),

                environment.isActive(),

                environment.isDeleted(),

                environment.getCreatedAt(),

                environment.getUpdatedAt()
        );
    }
}