package com.smartdeploy.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private ApplicationService applicationService;

    private Application application;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .id(1L)
                .name("SmartDeploy Front")
                .description("Interface Vue")
                .repositoryUrl(
                        "https://github.com/company/smartdeploy-front"
                )
                .technology(Technology.VUE)
                .active(true)
                .deleted(false)
                .build();
    }

    /* ==========================================================
     *                       CREATE
     * ==========================================================
     */

    @Test
    void shouldCreateApplicationSuccessfully() {
        CreateApplicationRequest request =
                new CreateApplicationRequest(
                        " SmartDeploy Backend ",
                        " Backend Spring Boot ",
                        " https://github.com/company/backend ",
                        Technology.SPRING_BOOT
                );

        when(
                applicationRepository
                        .existsByNameIgnoreCaseAndDeletedFalse(
                                "SmartDeploy Backend"
                        )
        ).thenReturn(false);

        when(
                applicationRepository
                        .existsByNameIgnoreCaseAndDeletedTrue(
                                "SmartDeploy Backend"
                        )
        ).thenReturn(false);

        when(
                applicationRepository.saveAndFlush(
                        any(Application.class)
                )
        ).thenAnswer(invocation -> {
            Application saved =
                    invocation.getArgument(0);

            saved.setId(2L);

            return saved;
        });

        ApplicationResponse response =
                applicationService.create(request);

        assertNotNull(response);

        assertEquals(
                2L,
                response.id()
        );

        assertEquals(
                "SmartDeploy Backend",
                response.name()
        );

        assertEquals(
                "Backend Spring Boot",
                response.description()
        );

        assertEquals(
                "https://github.com/company/backend",
                response.repositoryUrl()
        );

        assertEquals(
                Technology.SPRING_BOOT,
                response.technology()
        );

        assertTrue(response.active());
        assertFalse(response.deleted());

        ArgumentCaptor<Application> captor =
                ArgumentCaptor.forClass(
                        Application.class
                );

        verify(applicationRepository)
                .saveAndFlush(captor.capture());

        Application captured =
                captor.getValue();

        assertEquals(
                "SmartDeploy Backend",
                captured.getName()
        );

        assertEquals(
                "Backend Spring Boot",
                captured.getDescription()
        );
    }

    @Test
    void shouldThrowWhenActiveApplicationNameAlreadyExists() {
        CreateApplicationRequest request =
                new CreateApplicationRequest(
                        "SmartDeploy Front",
                        "Description",
                        "https://github.com/company/front",
                        Technology.VUE
                );

        when(
                applicationRepository
                        .existsByNameIgnoreCaseAndDeletedFalse(
                                "SmartDeploy Front"
                        )
        ).thenReturn(true);

        assertThrows(
                ApplicationAlreadyExistsException.class,
                () -> applicationService.create(request)
        );

        verify(
                applicationRepository,
                never()
        ).saveAndFlush(any());
    }

    @Test
    void shouldThrowWhenDeletedApplicationNameAlreadyExists() {
        CreateApplicationRequest request =
                new CreateApplicationRequest(
                        "Ancienne App",
                        "Description",
                        "https://github.com/company/old",
                        Technology.REACT
                );

        when(
                applicationRepository
                        .existsByNameIgnoreCaseAndDeletedFalse(
                                "Ancienne App"
                        )
        ).thenReturn(false);

        when(
                applicationRepository
                        .existsByNameIgnoreCaseAndDeletedTrue(
                                "Ancienne App"
                        )
        ).thenReturn(true);

        ApplicationAlreadyExistsException exception =
                assertThrows(
                        ApplicationAlreadyExistsException.class,
                        () ->
                                applicationService.create(
                                        request
                                )
                );

        assertTrue(
                exception
                        .getMessage()
                        .contains("Veuillez la restaurer")
        );

        verify(
                applicationRepository,
                never()
        ).saveAndFlush(any());
    }

    /* ==========================================================
     *                       FIND BY ID
     * ==========================================================
     */

    @Test
    void shouldFindApplicationById() {
        when(
                applicationRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(Optional.of(application));

        ApplicationResponse response =
                applicationService.findById(1L);

        assertEquals(
                1L,
                response.id()
        );

        assertEquals(
                "SmartDeploy Front",
                response.name()
        );
    }

    @Test
    void shouldThrowWhenApplicationDoesNotExist() {
        when(
                applicationRepository
                        .findByIdAndDeletedFalse(99L)
        ).thenReturn(Optional.empty());

        assertThrows(
                ApplicationNotFoundException.class,
                () ->
                        applicationService.findById(
                                99L
                        )
        );
    }

    /* ==========================================================
     *                       UPDATE
     * ==========================================================
     */

    @Test
    void shouldUpdateApplicationSuccessfully() {
        UpdateApplicationRequest request =
                new UpdateApplicationRequest(
                        " SmartDeploy UI ",
                        " Nouvelle description ",
                        " https://github.com/company/new-ui ",
                        Technology.VUE
                );

        when(
                applicationRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(Optional.of(application));

        when(
                applicationRepository
                        .existsByNameIgnoreCaseAndDeletedFalseAndIdNot(
                                "SmartDeploy UI",
                                1L
                        )
        ).thenReturn(false);

        when(
                applicationRepository
                        .existsByNameIgnoreCaseAndDeletedTrueAndIdNot(
                                "SmartDeploy UI",
                                1L
                        )
        ).thenReturn(false);

        when(
                applicationRepository.saveAndFlush(
                        application
                )
        ).thenReturn(application);

        ApplicationResponse response =
                applicationService.update(
                        1L,
                        request
                );

        assertEquals(
                "SmartDeploy UI",
                response.name()
        );

        assertEquals(
                "Nouvelle description",
                response.description()
        );

        assertEquals(
                "https://github.com/company/new-ui",
                response.repositoryUrl()
        );
    }

    @Test
    void shouldThrowWhenUpdatedNameAlreadyExists() {
        UpdateApplicationRequest request =
                new UpdateApplicationRequest(
                        "Existing App",
                        "Description",
                        "https://github.com/company/existing",
                        Technology.REACT
                );

        when(
                applicationRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(Optional.of(application));

        when(
                applicationRepository
                        .existsByNameIgnoreCaseAndDeletedFalseAndIdNot(
                                "Existing App",
                                1L
                        )
        ).thenReturn(true);

        assertThrows(
                ApplicationAlreadyExistsException.class,
                () ->
                        applicationService.update(
                                1L,
                                request
                        )
        );

        verify(
                applicationRepository,
                never()
        ).saveAndFlush(any());
    }

    /* ==========================================================
     *                  ACTIVATE / DEACTIVATE
     * ==========================================================
     */

    @Test
    void shouldActivateApplication() {
        application.setActive(false);

        when(
                applicationRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(Optional.of(application));

        when(
                applicationRepository.saveAndFlush(
                        application
                )
        ).thenReturn(application);

        ApplicationResponse response =
                applicationService.activate(1L);

        assertTrue(response.active());
        assertTrue(application.isActive());
    }

    @Test
    void shouldDeactivateApplication() {
        when(
                applicationRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(Optional.of(application));

        when(
                applicationRepository.saveAndFlush(
                        application
                )
        ).thenReturn(application);

        ApplicationResponse response =
                applicationService.deactivate(1L);

        assertFalse(response.active());
        assertFalse(application.isActive());
    }

    /* ==========================================================
     *                       SOFT DELETE
     * ==========================================================
     */

    @Test
    void shouldSoftDeleteApplication() {
        when(
                applicationRepository
                        .findByIdAndDeletedFalse(1L)
        ).thenReturn(Optional.of(application));

        applicationService.softDelete(1L);

        assertTrue(application.isDeleted());
        assertFalse(application.isActive());

        verify(applicationRepository)
                .saveAndFlush(application);
    }

    /* ==========================================================
     *                       RESTORE
     * ==========================================================
     */

    @Test
    void shouldRestoreApplication() {
        application.setDeleted(true);
        application.setActive(false);

        when(
                applicationRepository
                        .findByIdAndDeletedTrue(1L)
        ).thenReturn(Optional.of(application));

        when(
                applicationRepository
                        .existsByNameIgnoreCaseAndDeletedFalseAndIdNot(
                                "SmartDeploy Front",
                                1L
                        )
        ).thenReturn(false);

        when(
                applicationRepository
                        .existsByNameIgnoreCaseAndDeletedTrueAndIdNot(
                                "SmartDeploy Front",
                                1L
                        )
        ).thenReturn(false);

        when(
                applicationRepository.saveAndFlush(
                        application
                )
        ).thenReturn(application);

        ApplicationResponse response =
                applicationService.restore(1L);

        assertFalse(response.deleted());
        assertTrue(response.active());
    }

    /* ==========================================================
     *                       SEARCH
     * ==========================================================
     */

    @Test
    void shouldSearchApplications() {
        Page<Application> page =
                new PageImpl<>(
                        List.of(application)
                );

        when(
                applicationRepository
                        .searchApplications(
                                eq("smart"),
                                eq(Technology.VUE),
                                eq(true),
                                any(Pageable.class)
                        )
        ).thenReturn(page);

        ApplicationPageResponse response =
                applicationService
                        .searchApplications(
                                0,
                                10,
                                " smart ",
                                Technology.VUE,
                                true
                        );

        assertEquals(
                1,
                response.content().size()
        );

        assertEquals(
                "SmartDeploy Front",
                response.content()
                        .get(0)
                        .name()
        );
    }

    @Test
    void shouldUseEmptySearchWhenSearchIsBlank() {
        Page<Application> page =
                new PageImpl<>(
                        List.of()
                );

        when(
                applicationRepository
                        .searchApplications(
                                eq(""),
                                isNull(),
                                isNull(),
                                any(Pageable.class)
                        )
        ).thenReturn(page);

        applicationService.searchApplications(
                0,
                10,
                "   ",
                null,
                null
        );

        verify(applicationRepository)
                .searchApplications(
                        eq(""),
                        isNull(),
                        isNull(),
                        any(Pageable.class)
                );
    }
}