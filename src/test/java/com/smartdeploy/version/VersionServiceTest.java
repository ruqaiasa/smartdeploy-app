package com.smartdeploy.version;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.ApplicationNotFoundException;
import com.smartdeploy.application.ApplicationRepository;
import com.smartdeploy.application.Technology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock
    private VersionRepository versionRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    private VersionService versionService;

    @BeforeEach
    void setUp() {
        versionService = new VersionService(
                versionRepository,
                applicationRepository
        );
    }

    /* ==========================================================
     *                      CREATE
     * ==========================================================
     */

    @Test
    void create_shouldCreatePendingVersion() {

        Application application = createApplication();

        CreateVersionRequest request = new CreateVersionRequest(
                1L,
                " 1.0.0 ",
                " main ",
                " abc123 ",
                " smartdeploy/backend:1.0.0 ",
                " target/app.jar ",
                " https://jenkins/build/10 "
        );

        when(applicationRepository.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(application));

        when(versionRepository
                .existsByApplicationAndVersionAndDeletedFalse(
                        application,
                        "1.0.0"
                ))
                .thenReturn(false);

        when(versionRepository.saveAndFlush(any(Version.class)))
                .thenAnswer(invocation -> {
                    Version version = invocation.getArgument(0);

                    version.setId(10L);
                    version.setCreatedAt(LocalDateTime.now());
                    version.setUpdatedAt(LocalDateTime.now());

                    return version;
                });

        VersionResponse response =
                versionService.create(request);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals(1L, response.applicationId());
        assertEquals("1.0.0", response.version());
        assertEquals("main", response.gitBranch());
        assertEquals("abc123", response.gitCommit());
        assertEquals(BuildStatus.PENDING, response.buildStatus());
        assertFalse(response.deleted());

        verify(versionRepository)
                .saveAndFlush(any(Version.class));
    }

    @Test
    void create_shouldThrowWhenApplicationDoesNotExist() {

        CreateVersionRequest request = new CreateVersionRequest(
                999L,
                "1.0.0",
                "main",
                null,
                null,
                null,
                null
        );

        when(applicationRepository.findByIdAndDeletedFalse(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ApplicationNotFoundException.class,
                () -> versionService.create(request)
        );

        verifyNoInteractions(versionRepository);
    }

    @Test
    void create_shouldThrowWhenVersionAlreadyExists() {

        Application application = createApplication();

        CreateVersionRequest request = new CreateVersionRequest(
                1L,
                "1.0.0",
                "main",
                null,
                null,
                null,
                null
        );

        when(applicationRepository.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(application));

        when(versionRepository
                .existsByApplicationAndVersionAndDeletedFalse(
                        application,
                        "1.0.0"
                ))
                .thenReturn(true);

        assertThrows(
                VersionAlreadyExistsException.class,
                () -> versionService.create(request)
        );

        verify(versionRepository, never())
                .saveAndFlush(any(Version.class));
    }

    /* ==========================================================
     *                      FIND
     * ==========================================================
     */

    @Test
    void findById_shouldReturnVersion() {

        Version version =
                createVersion(BuildStatus.SUCCESS, false);

        when(versionRepository.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(version));

        VersionResponse response =
                versionService.findById(1L);

        assertEquals(1L, response.id());
        assertEquals("1.0.0", response.version());
        assertEquals(BuildStatus.SUCCESS, response.buildStatus());
    }

    @Test
    void findById_shouldThrowWhenVersionDoesNotExist() {

        when(versionRepository.findByIdAndDeletedFalse(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                VersionNotFoundException.class,
                () -> versionService.findById(999L)
        );
    }

    /* ==========================================================
     *                  BUILD STATUS
     * ==========================================================
     */

    @Test
    void updateBuildStatus_shouldChangeStatus() {

        Version version =
                createVersion(BuildStatus.PENDING, false);

        when(versionRepository.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(version));

        when(versionRepository.saveAndFlush(version))
                .thenReturn(version);

        VersionResponse response =
                versionService.updateBuildStatus(
                        1L,
                        BuildStatus.RUNNING
                );

        assertEquals(BuildStatus.RUNNING, version.getBuildStatus());
        assertEquals(BuildStatus.RUNNING, response.buildStatus());

        verify(versionRepository).saveAndFlush(version);
    }

    @Test
    void updateBuildStatus_shouldThrowWhenStatusIsUnchanged() {

        Version version =
                createVersion(BuildStatus.SUCCESS, false);

        when(versionRepository.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(version));

        assertThrows(
                VersionStateException.class,
                () -> versionService.updateBuildStatus(
                        1L,
                        BuildStatus.SUCCESS
                )
        );

        verify(versionRepository, never())
                .saveAndFlush(any(Version.class));
    }

    /* ==========================================================
     *                  BUILD METADATA
     * ==========================================================
     */

    @Test
    void updateBuildMetadata_shouldUpdateJenkinsData() {

        Version version =
                createVersion(BuildStatus.RUNNING, false);

        when(versionRepository.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(version));

        when(versionRepository.saveAndFlush(version))
                .thenReturn(version);

        VersionResponse response =
                versionService.updateBuildMetadata(
                        1L,
                        42,
                        "smartdeploy/backend:1.0.0",
                        "target/backend.jar",
                        "https://jenkins/build/42"
                );

        assertEquals(42, response.buildNumber());
        assertEquals(
                "smartdeploy/backend:1.0.0",
                response.dockerImage()
        );
        assertEquals(
                "target/backend.jar",
                response.artifactPath()
        );
    }

    /* ==========================================================
     *                  SOFT DELETE / RESTORE
     * ==========================================================
     */

    @Test
    void softDelete_shouldMarkVersionDeleted() {

        Version version =
                createVersion(BuildStatus.SUCCESS, false);

        when(versionRepository.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(version));

        when(versionRepository.saveAndFlush(version))
                .thenReturn(version);

        versionService.softDelete(1L);

        assertTrue(version.isDeleted());

        verify(versionRepository).saveAndFlush(version);
    }

    @Test
    void softDelete_shouldThrowWhenBuildIsRunning() {

        Version version =
                createVersion(BuildStatus.RUNNING, false);

        when(versionRepository.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(version));

        assertThrows(
                VersionStateException.class,
                () -> versionService.softDelete(1L)
        );

        verify(versionRepository, never())
                .saveAndFlush(any(Version.class));
    }

    @Test
    void restore_shouldRestoreVersion() {

        Version version =
                createVersion(BuildStatus.SUCCESS, true);

        when(versionRepository.findById(1L))
                .thenReturn(Optional.of(version));

        when(versionRepository
                .existsByApplicationAndVersionAndDeletedFalse(
                        version.getApplication(),
                        version.getVersion()
                ))
                .thenReturn(false);

        when(versionRepository.saveAndFlush(version))
                .thenReturn(version);

        VersionResponse response =
                versionService.restore(1L);

        assertFalse(version.isDeleted());
        assertFalse(response.deleted());

        verify(versionRepository).saveAndFlush(version);
    }

    /* ==========================================================
     *                      HELPERS
     * ==========================================================
     */

    private Application createApplication() {

        LocalDateTime now = LocalDateTime.now();

        return Application.builder()
                .id(1L)
                .name("SmartDeploy Backend")
                .description("Backend Spring Boot")
                .repositoryUrl("https://github.com/company/backend")
                .technology(Technology.SPRING_BOOT)
                .active(true)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Version createVersion(
            BuildStatus buildStatus,
            boolean deleted
    ) {
        LocalDateTime now = LocalDateTime.now();

        return Version.builder()
                .id(1L)
                .application(createApplication())
                .version("1.0.0")
                .gitBranch("main")
                .gitCommit("abc123")
                .dockerImage("smartdeploy/backend:1.0.0")
                .buildStatus(buildStatus)
                .buildNumber(10)
                .artifactPath("target/backend.jar")
                .buildLogsUrl("https://jenkins/build/10")
                .deleted(deleted)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}