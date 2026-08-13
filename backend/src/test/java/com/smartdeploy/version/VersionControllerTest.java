package com.smartdeploy.version;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VersionControllerTest {

    @Mock
    private VersionService versionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        VersionController controller =
                new VersionController(versionService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void create_shouldReturn201AndVersion()
            throws Exception {

        CreateVersionRequest request =
                new CreateVersionRequest(
                        1L,
                        "1.0.0",
                        "main",
                        "abc123",
                        null,
                        null,
                        null
                );

        when(versionService.create(request))
                .thenReturn(createResponse(
                        BuildStatus.PENDING,
                        false
                ));

        mockMvc.perform(
                        post("/api/versions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "applicationId": 1,
                                          "version": "1.0.0",
                                          "gitBranch": "main",
                                          "gitCommit": "abc123",
                                          "dockerImage": null,
                                          "artifactPath": null,
                                          "buildLogsUrl": null
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.buildStatus")
                        .value("PENDING"))
                .andExpect(jsonPath("$.deleted").value(false));
    }

    @Test
    void create_shouldReturn400WhenRequestIsInvalid()
            throws Exception {

        mockMvc.perform(
                        post("/api/versions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "applicationId": null,
                                          "version": "",
                                          "gitBranch": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(versionService);
    }

    @Test
    void findById_shouldReturn200AndVersion()
            throws Exception {

        when(versionService.findById(1L))
                .thenReturn(createResponse(
                        BuildStatus.SUCCESS,
                        false
                ));

        mockMvc.perform(get("/api/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.buildStatus")
                        .value("SUCCESS"));
    }

    @Test
    void searchVersions_shouldReturnPage()
            throws Exception {

        VersionPageResponse pageResponse =
                new VersionPageResponse(
                        List.of(
                                createResponse(
                                        BuildStatus.SUCCESS,
                                        false
                                )
                        ),
                        0,
                        10,
                        1,
                        1,
                        true,
                        true
                );

        when(versionService.searchVersions(
                0,
                10,
                null,
                null,
                null
        )).thenReturn(pageResponse);

        mockMvc.perform(
                        get("/api/versions")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()")
                        .value(1))
                .andExpect(jsonPath("$.totalElements")
                        .value(1));
    }

    @Test
    void updateBuildStatus_shouldReturnUpdatedStatus()
            throws Exception {

        when(versionService.updateBuildStatus(
                1L,
                BuildStatus.RUNNING
        )).thenReturn(createResponse(
                BuildStatus.RUNNING,
                false
        ));

        mockMvc.perform(
                        patch("/api/versions/1/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "buildStatus": "RUNNING"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildStatus")
                        .value("RUNNING"));
    }

    @Test
    void softDelete_shouldReturn204()
            throws Exception {

        doNothing()
                .when(versionService)
                .softDelete(1L);

        mockMvc.perform(delete("/api/versions/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(versionService).softDelete(1L);
    }

    @Test
    void restore_shouldReturnRestoredVersion()
            throws Exception {

        when(versionService.restore(1L))
                .thenReturn(createResponse(
                        BuildStatus.SUCCESS,
                        false
                ));

        mockMvc.perform(
                        patch("/api/versions/1/restore")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted")
                        .value(false));
    }

    private VersionResponse createResponse(
            BuildStatus status,
            boolean deleted
    ) {
        LocalDateTime now = LocalDateTime.now();

        return new VersionResponse(
                1L,
                1L,
                "SmartDeploy Backend",
                "1.0.0",
                "main",
                "abc123",
                "smartdeploy/backend:1.0.0",
                status,
                10,
                "target/backend.jar",
                "https://jenkins/build/10",
                deleted,
                now,
                now
        );
    }
}