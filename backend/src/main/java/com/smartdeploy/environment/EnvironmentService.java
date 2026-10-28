package com.smartdeploy.environment;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.ApplicationNotFoundException;
import com.smartdeploy.application.ApplicationRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EnvironmentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final EnvironmentRepository environmentRepository;
    private final ApplicationRepository applicationRepository;

    public EnvironmentService(
            EnvironmentRepository environmentRepository,
            ApplicationRepository applicationRepository
    ) {
        this.environmentRepository = environmentRepository;
        this.applicationRepository = applicationRepository;
    }

    /* ==========================================================
     *                  CREATE ENVIRONMENT
     * ==========================================================
     */

    public EnvironmentResponse create(
            CreateEnvironmentRequest request
    ) {
        // 1. Vérifier que l'application existe
        Application application =
                findActiveApplication(
                        request.applicationId()
                );

        // 2. Vérifier l'unicité du type d'environnement
        checkEnvironmentAvailability(
                application,
                request.type(),
                null
        );

        // 3. Générer automatiquement le namespace Kubernetes
        String generatedNamespace =
                EnvironmentNamespaceGenerator.generate(
                        application.getName(),
                        request.type()
                );

        // 4. Créer l'environnement
        Environment environment =
                Environment.builder()
                        .application(application)
                        .type(request.type())

                        .name(
                                normalizeRequiredText(
                                        request.name()
                                )
                        )

                        .description(
                                normalizeNullableText(
                                        request.description()
                                )
                        )

                        // Namespace généré automatiquement
                        .namespace(generatedNamespace)

                        // Configuration du cluster conservée
                        .clusterUrl(
                                normalizeNullableText(
                                        request.clusterUrl()
                                )
                        )

                        .active(true)
                        .deleted(false)
                        .build();

        // 5. Enregistrer dans PostgreSQL
        Environment savedEnvironment =
                environmentRepository.saveAndFlush(
                        environment
                );

        return toResponse(savedEnvironment);
    }

    /* ==========================================================
     *                  GET ENVIRONMENT BY ID
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public EnvironmentResponse findById(Long id) {
        Environment environment =
                findExistingEnvironment(id);

        return toResponse(environment);
    }

    /* ==========================================================
     *              SEARCH AND PAGINATE ENVIRONMENTS
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public EnvironmentPageResponse searchEnvironments(
            int page,
            int size,
            String search,
            EnvironmentType type,
            Boolean active,
            Long applicationId
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

        Page<Environment> environmentPage =
                environmentRepository.searchEnvironments(
                        normalizedSearch,
                        type,
                        active,
                        applicationId,
                        pageable
                );

        return new EnvironmentPageResponse(
                environmentPage
                        .getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),

                environmentPage.getNumber(),
                environmentPage.getSize(),
                environmentPage.getTotalElements(),
                environmentPage.getTotalPages(),
                environmentPage.isFirst(),
                environmentPage.isLast()
        );
    }
    /* ==========================================================
     *                  UPDATE ENVIRONMENT
     * ==========================================================
     */

    public EnvironmentResponse update(
            Long id,
            UpdateEnvironmentRequest request
    ) {
        Environment environment =
                findExistingEnvironment(id);

        Application application =
                findActiveApplication(
                        request.applicationId()
                );

        checkEnvironmentAvailability(
                application,
                request.type(),
                environment.getId()
        );

        environment.setApplication(application);

        environment.setType(
                request.type()
        );

        environment.setName(
                normalizeRequiredText(
                        request.name()
                )
        );

        environment.setDescription(
                normalizeNullableText(
                        request.description()
                )
        );

        environment.setNamespace(
                normalizeRequiredText(
                        request.namespace()
                )
        );

        environment.setClusterUrl(
                normalizeNullableText(
                        request.clusterUrl()
                )
        );

        Environment updatedEnvironment =
                environmentRepository.saveAndFlush(
                        environment
                );

        return toResponse(updatedEnvironment);
    }

    /* ==========================================================
     *                  ACTIVATE ENVIRONMENT
     * ==========================================================
     */

    public EnvironmentResponse activate(Long id) {
        Environment environment =
                findExistingEnvironment(id);

        if (!environment.isActive()) {
            environment.setActive(true);
        }

        Environment activatedEnvironment =
                environmentRepository.saveAndFlush(
                        environment
                );

        return toResponse(activatedEnvironment);
    }

    /* ==========================================================
     *                  DEACTIVATE ENVIRONMENT
     * ==========================================================
     */

    public EnvironmentResponse deactivate(Long id) {
        Environment environment =
                findExistingEnvironment(id);

        if (environment.isActive()) {
            environment.setActive(false);
        }

        Environment deactivatedEnvironment =
                environmentRepository.saveAndFlush(
                        environment
                );

        return toResponse(deactivatedEnvironment);
    }

    /* ==========================================================
     *                  SOFT DELETE ENVIRONMENT
     * ==========================================================
     */

    public void softDelete(Long id) {
        Environment environment =
                findExistingEnvironment(id);

        environment.setDeleted(true);
        environment.setActive(false);

        environmentRepository.saveAndFlush(
                environment
        );
    }

    /* ==========================================================
     *                  RESTORE ENVIRONMENT
     * ==========================================================
     */

    public EnvironmentResponse restore(Long id) {
        Environment environment =
                environmentRepository
                        .findByIdAndDeletedTrue(id)
                        .orElseThrow(() ->
                                new EnvironmentNotFoundException(
                                        id
                                )
                        );

        Application application =
                environment.getApplication();

        if (
                application == null
                        || application.isDeleted()
        ) {
            throw new IllegalStateException(
                    "Impossible de restaurer cet environnement : "
                            + "l’application associée est supprimée."
            );
        }

        checkEnvironmentAvailability(
                application,
                environment.getType(),
                environment.getId()
        );

        environment.setDeleted(false);
        environment.setActive(true);

        Environment restoredEnvironment =
                environmentRepository.saveAndFlush(
                        environment
                );

        return toResponse(restoredEnvironment);
    }

    /* ==========================================================
     *                  PRIVATE FIND METHODS
     * ==========================================================
     */

    private Environment findExistingEnvironment(
            Long id
    ) {
        return environmentRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() ->
                        new EnvironmentNotFoundException(id)
                );
    }

    private Application findActiveApplication(
            Long applicationId
    ) {
        return applicationRepository
                .findByIdAndDeletedFalse(applicationId)
                .orElseThrow(() ->
                        new ApplicationNotFoundException(
                                applicationId
                        )
                );
    }

    /* ==========================================================
     *                  BUSINESS VALIDATION
     * ==========================================================
     */

    private void checkEnvironmentAvailability(
            Application application,
            EnvironmentType type,
            Long excludedEnvironmentId
    ) {
        boolean alreadyExists;

        if (excludedEnvironmentId == null) {
            alreadyExists =
                    environmentRepository
                            .existsByApplicationAndTypeAndDeletedFalse(
                                    application,
                                    type
                            );
        } else {
            alreadyExists =
                    environmentRepository
                            .existsByApplicationAndTypeAndDeletedFalseAndIdNot(
                                    application,
                                    type,
                                    excludedEnvironmentId
                            );
        }

        if (alreadyExists) {
            throw new EnvironmentAlreadyExistsException(
                    application.getName(),
                    type
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

    private EnvironmentResponse toResponse(
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