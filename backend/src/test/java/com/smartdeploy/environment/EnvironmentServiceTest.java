

package com.smartdeploy.environment;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.ApplicationNotFoundException;
import com.smartdeploy.application.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvironmentServiceTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private EnvironmentService environmentService;

    private Application application;
    private Environment environment;

    @BeforeEach
    void setUp() {

        application = Application.builder()
                .id(1L)
                .name("SmartDeploy")
                .active(true)
                .deleted(false)
                .build();

        environment = Environment.builder()
                .id(10L)
                .application(application)
                .type(EnvironmentType.DEVELOPMENT)
                .name("Development")
                .description("Environnement de développement")
                .namespace("smartdeploy-dev")
                .clusterUrl("https://dev.k8s.smartdeploy.local")
                .active(true)
                .deleted(false)
                .build();
    }

    /* ==========================================================
     *                       CREATE
     * ========================================================== */

    @Test
    void shouldCreateEnvironmentSuccessfully() {

        CreateEnvironmentRequest request =
                new CreateEnvironmentRequest(
                        1L,
                        EnvironmentType.DEVELOPMENT,
                        " Development ",
                        " Environnement de développement ",
                        " smartdeploy-dev ",
                        " https://dev.k8s.smartdeploy.local "
                );

        when(
                applicationRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(Optional.of(application));

        when(
                environmentRepository
                        .existsByApplicationAndTypeAndDeletedFalse(
                                application,
                                EnvironmentType.DEVELOPMENT
                        )
        ).thenReturn(false);

        when(
                environmentRepository.saveAndFlush(
                        any(Environment.class)
                )
        ).thenAnswer(invocation -> {

            Environment savedEnvironment =
                    invocation.getArgument(0);

            savedEnvironment.setId(10L);

            return savedEnvironment;
        });

        EnvironmentResponse response =
                environmentService.create(request);

        assertNotNull(response);

        assertEquals(
                10L,
                response.id()
        );

        assertEquals(
                1L,
                response.applicationId()
        );

        assertEquals(
                "SmartDeploy",
                response.applicationName()
        );

        assertEquals(
                EnvironmentType.DEVELOPMENT,
                response.type()
        );

        assertEquals(
                "Development",
                response.name()
        );

        assertEquals(
                "Environnement de développement",
                response.description()
        );

        assertEquals(
                "smartdeploy-dev",
                response.namespace()
        );

        assertEquals(
                "https://dev.k8s.smartdeploy.local",
                response.clusterUrl()
        );

        assertTrue(response.active());
        assertFalse(response.deleted());

        ArgumentCaptor<Environment> captor =
                ArgumentCaptor.forClass(
                        Environment.class
                );

        verify(environmentRepository)
                .saveAndFlush(captor.capture());

        Environment capturedEnvironment =
                captor.getValue();

        assertEquals(
                "Development",
                capturedEnvironment.getName()
        );

        assertEquals(
                "Environnement de développement",
                capturedEnvironment.getDescription()
        );

        assertEquals(
                "smartdeploy-dev",
                capturedEnvironment.getNamespace()
        );
    }

    @Test
    void shouldCreateEnvironmentWithNullOptionalFields() {

        CreateEnvironmentRequest request =
                new CreateEnvironmentRequest(
                        1L,
                        EnvironmentType.TEST,
                        "Testing",
                        null,
                        "smartdeploy-test",
                        null
                );

        when(
                applicationRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(Optional.of(application));

        when(
                environmentRepository
                        .existsByApplicationAndTypeAndDeletedFalse(
                                application,
                                EnvironmentType.TEST
                        )
        ).thenReturn(false);

        when(
                environmentRepository.saveAndFlush(
                        any(Environment.class)
                )
        ).thenAnswer(invocation -> {

            Environment savedEnvironment =
                    invocation.getArgument(0);

            savedEnvironment.setId(11L);

            return savedEnvironment;
        });

        EnvironmentResponse response =
                environmentService.create(request);

        assertEquals(
                11L,
                response.id()
        );

        assertNull(response.description());
        assertNull(response.clusterUrl());
    }

    @Test
    void shouldThrowWhenApplicationDoesNotExistDuringCreation() {

        CreateEnvironmentRequest request =
                new CreateEnvironmentRequest(
                        99L,
                        EnvironmentType.DEVELOPMENT,
                        "Development",
                        "Description",
                        "smartdeploy-dev",
                        null
                );

        when(
                applicationRepository
                        .findByIdAndDeletedFalse(99L)
        ).thenReturn(Optional.empty());

        assertThrows(
                ApplicationNotFoundException.class,
                () -> environmentService.create(request)
        );

        verify(
                environmentRepository,
                never()
        ).saveAndFlush(any());
    }

    @Test
    void shouldThrowWhenEnvironmentTypeAlreadyExistsForApplication() {

        CreateEnvironmentRequest request =
                new CreateEnvironmentRequest(
                        1L,
                        EnvironmentType.DEVELOPMENT,
                        "Development",
                        "Description",
                        "smartdeploy-dev",
                        null
                );

        when(
                applicationRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(Optional.of(application));

        when(
                environmentRepository
                        .existsByApplicationAndTypeAndDeletedFalse(
                                application,
                                EnvironmentType.DEVELOPMENT
                        )
        ).thenReturn(true);

        assertThrows(
                EnvironmentAlreadyExistsException.class,
                () -> environmentService.create(request)
        );

        verify(
                environmentRepository,
                never()
        ).saveAndFlush(any());
    }

    /* ==========================================================
     *                       FIND BY ID
     * ========================================================== */

    @Test
    void shouldFindEnvironmentById() {

        when(
                environmentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(Optional.of(environment));

        EnvironmentResponse response =
                environmentService.findById(10L);

        assertEquals(
                10L,
                response.id()
        );

        assertEquals(
                "Development",
                response.name()
        );

        assertEquals(
                "Environnement de développement",
                response.description()
        );

        assertEquals(
                "SmartDeploy",
                response.applicationName()
        );
    }

    @Test
    void shouldThrowWhenEnvironmentDoesNotExist() {

        when(
                environmentRepository
                        .findByIdAndDeletedFalse(99L)
        ).thenReturn(Optional.empty());

        assertThrows(
                EnvironmentNotFoundException.class,
                () -> environmentService.findById(99L)
        );
    }

    /* ==========================================================
     *                       UPDATE
     * ========================================================== */

    @Test
    void shouldUpdateEnvironmentSuccessfully() {

        UpdateEnvironmentRequest request =
                new UpdateEnvironmentRequest(
                        1L,
                        EnvironmentType.STAGING,
                        " Staging ",
                        " Environnement de préproduction ",
                        " smartdeploy-staging ",
                        " https://staging.k8s.smartdeploy.local "
                );

        when(
                environmentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(Optional.of(environment));

        when(
                applicationRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(Optional.of(application));

        when(
                environmentRepository
                        .existsByApplicationAndTypeAndDeletedFalseAndIdNot(
                                application,
                                EnvironmentType.STAGING,
                                10L
                        )
        ).thenReturn(false);

        when(
                environmentRepository.saveAndFlush(
                        any(Environment.class)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        EnvironmentResponse response =
                environmentService.update(
                        10L,
                        request
                );

        assertEquals(
                EnvironmentType.STAGING,
                response.type()
        );

        assertEquals(
                "Staging",
                response.name()
        );

        assertEquals(
                "Environnement de préproduction",
                response.description()
        );

        assertEquals(
                "smartdeploy-staging",
                response.namespace()
        );

        assertEquals(
                "https://staging.k8s.smartdeploy.local",
                response.clusterUrl()
        );
    }

    @Test
    void shouldThrowWhenUpdatedTypeAlreadyExists() {

        UpdateEnvironmentRequest request =
                new UpdateEnvironmentRequest(
                        1L,
                        EnvironmentType.PRODUCTION,
                        "Production",
                        "Production",
                        "smartdeploy-prod",
                        null
                );

        when(
                environmentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(Optional.of(environment));

        when(
                applicationRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(Optional.of(application));

        when(
                environmentRepository
                        .existsByApplicationAndTypeAndDeletedFalseAndIdNot(
                                application,
                                EnvironmentType.PRODUCTION,
                                10L
                        )
        ).thenReturn(true);

        assertThrows(
                EnvironmentAlreadyExistsException.class,
                () -> environmentService.update(
                        10L,
                        request
                )
        );

        verify(
                environmentRepository,
                never()
        ).saveAndFlush(any());
    }

    /* ==========================================================
     *                   ACTIVATE / DEACTIVATE
     * ========================================================== */

    @Test
    void shouldActivateEnvironment() {

        environment.setActive(false);

        when(
                environmentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(Optional.of(environment));

        when(
                environmentRepository.saveAndFlush(environment)
        ).thenReturn(environment);

        EnvironmentResponse response =
                environmentService.activate(10L);

        assertTrue(response.active());
        assertTrue(environment.isActive());

        verify(environmentRepository)
                .saveAndFlush(environment);
    }

    @Test
    void shouldDeactivateEnvironment() {

        environment.setActive(true);

        when(
                environmentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(Optional.of(environment));

        when(
                environmentRepository.saveAndFlush(environment)
        ).thenReturn(environment);

        EnvironmentResponse response =
                environmentService.deactivate(10L);

        assertFalse(response.active());
        assertFalse(environment.isActive());

        verify(environmentRepository)
                .saveAndFlush(environment);
    }

    /* ==========================================================
     *                       SOFT DELETE
     * ========================================================== */

    @Test
    void shouldSoftDeleteEnvironment() {

        when(
                environmentRepository
                        .findByIdAndDeletedFalse(10L)
        ).thenReturn(Optional.of(environment));

        environmentService.softDelete(10L);

        assertTrue(environment.isDeleted());
        assertFalse(environment.isActive());

        verify(environmentRepository)
                .saveAndFlush(environment);
    }

    /* ==========================================================
     *                       RESTORE
     * ========================================================== */

    @Test
    void shouldRestoreEnvironment() {

        environment.setDeleted(true);
        environment.setActive(false);

        when(
                environmentRepository
                        .findByIdAndDeletedTrue(10L)
        ).thenReturn(Optional.of(environment));

        when(
                environmentRepository
                        .existsByApplicationAndTypeAndDeletedFalseAndIdNot(
                                application,
                                EnvironmentType.DEVELOPMENT,
                                10L
                        )
        ).thenReturn(false);

        when(
                environmentRepository.saveAndFlush(environment)
        ).thenReturn(environment);

        EnvironmentResponse response =
                environmentService.restore(10L);

        assertFalse(response.deleted());
        assertTrue(response.active());

        assertFalse(environment.isDeleted());
        assertTrue(environment.isActive());
    }

    @Test
    void shouldThrowWhenRestoringEnvironmentWithDeletedApplication() {

        environment.setDeleted(true);
        application.setDeleted(true);

        when(
                environmentRepository
                        .findByIdAndDeletedTrue(10L)
        ).thenReturn(Optional.of(environment));

        assertThrows(
                IllegalStateException.class,
                () -> environmentService.restore(10L)
        );

        verify(
                environmentRepository,
                never()
        ).saveAndFlush(any());
    }

    @Test
    void shouldThrowWhenRestoredEnvironmentConflictsWithExistingOne() {

        environment.setDeleted(true);
        environment.setActive(false);

        when(
                environmentRepository
                        .findByIdAndDeletedTrue(10L)
        ).thenReturn(Optional.of(environment));

        when(
                environmentRepository
                        .existsByApplicationAndTypeAndDeletedFalseAndIdNot(
                                application,
                                EnvironmentType.DEVELOPMENT,
                                10L
                        )
        ).thenReturn(true);

        assertThrows(
                EnvironmentAlreadyExistsException.class,
                () -> environmentService.restore(10L)
        );

        verify(
                environmentRepository,
                never()
        ).saveAndFlush(any());
    }

    /* ==========================================================
     *                       SEARCH
     * ========================================================== */

    @Test
    void shouldSearchAndPaginateEnvironments() {

        @SuppressWarnings("unchecked")
        Page<Environment> environmentPage =
                mock(Page.class);

        when(
                environmentRepository.searchEnvironments(
                        eq("dev"),
                        eq(EnvironmentType.DEVELOPMENT),
                        eq(true),
                        eq(1L),
                        any(Pageable.class)
                )
        ).thenReturn(environmentPage);

        when(
                environmentPage.getContent()
        ).thenReturn(List.of(environment));

        when(
                environmentPage.getNumber()
        ).thenReturn(0);

        when(
                environmentPage.getSize()
        ).thenReturn(10);

        when(
                environmentPage.getTotalElements()
        ).thenReturn(1L);

        when(
                environmentPage.getTotalPages()
        ).thenReturn(1);

        when(
                environmentPage.isFirst()
        ).thenReturn(true);

        when(
                environmentPage.isLast()
        ).thenReturn(true);

        EnvironmentPageResponse response =
                environmentService.searchEnvironments(
                        0,
                        10,
                        " dev ",
                        EnvironmentType.DEVELOPMENT,
                        true,
                        1L
                );

        assertNotNull(response);

        assertEquals(
                1,
                response.content().size()
        );

        assertEquals(
                "Development",
                response.content()
                        .get(0)
                        .name()
        );

        assertEquals(
                "Environnement de développement",
                response.content()
                        .get(0)
                        .description()
        );

        assertEquals(
                0,
                response.page()
        );

        assertEquals(
                10,
                response.size()
        );

        assertEquals(
                1L,
                response.totalElements()
        );

        assertEquals(
                1,
                response.totalPages()
        );

        assertTrue(response.first());
        assertTrue(response.last());
    }

    @Test
    void shouldNormalizeInvalidPaginationValues() {

        @SuppressWarnings("unchecked")
        Page<Environment> environmentPage =
                mock(Page.class);

        when(
                environmentRepository.searchEnvironments(
                        eq(""),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(Pageable.class)
                )
        ).thenReturn(environmentPage);

        when(
                environmentPage.getContent()
        ).thenReturn(List.of());

        when(
                environmentPage.getNumber()
        ).thenReturn(0);

        when(
                environmentPage.getSize()
        ).thenReturn(100);

        when(
                environmentPage.getTotalElements()
        ).thenReturn(0L);

        when(
                environmentPage.getTotalPages()
        ).thenReturn(0);

        when(
                environmentPage.isFirst()
        ).thenReturn(true);

        when(
                environmentPage.isLast()
        ).thenReturn(true);

        environmentService.searchEnvironments(
                -5,
                500,
                "   ",
                null,
                null,
                null
        );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(environmentRepository)
                .searchEnvironments(
                        eq(""),
                        isNull(),
                        isNull(),
                        isNull(),
                        pageableCaptor.capture()
                );

        Pageable pageable =
                pageableCaptor.getValue();

        assertEquals(
                0,
                pageable.getPageNumber()
        );

        assertEquals(
                100,
                pageable.getPageSize()
        );
    }
}
