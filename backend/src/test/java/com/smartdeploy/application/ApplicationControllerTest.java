package com.smartdeploy.application;

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
class ApplicationControllerTest {

    @Mock
    private ApplicationService applicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        ApplicationController controller =
                new ApplicationController(applicationService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    /* ==========================================================
     *                  CREATE APPLICATION
     * ==========================================================
     */

    @Test
    void create_shouldReturn201AndApplication() throws Exception {

        CreateApplicationRequest request =
                new CreateApplicationRequest(
                        "SmartDeploy Backend",
                        "Backend Spring Boot",
                        "https://github.com/company/backend",
                        Technology.SPRING_BOOT
                );

        ApplicationResponse response =
                createResponse(
                        1L,
                        "SmartDeploy Backend",
                        true,
                        false
                );

        when(applicationService.create(request))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/applications")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "name": "SmartDeploy Backend",
                                          "description": "Backend Spring Boot",
                                          "repositoryUrl": "https://github.com/company/backend",
                                          "technology": "SPRING_BOOT"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(content()
                        .contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        ))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name")
                        .value("SmartDeploy Backend"))
                .andExpect(jsonPath("$.technology")
                        .value("SPRING_BOOT"))
                .andExpect(jsonPath("$.active")
                        .value(true))
                .andExpect(jsonPath("$.deleted")
                        .value(false));

        verify(applicationService).create(request);
    }

    @Test
    void create_shouldReturn400WhenRequestIsInvalid()
            throws Exception {

        mockMvc.perform(
                        post("/api/applications")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "name": "",
                                          "description": "Test",
                                          "repositoryUrl": "",
                                          "technology": null
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(applicationService);
    }

    /* ==========================================================
     *                  FIND APPLICATION
     * ==========================================================
     */

    @Test
    void findById_shouldReturn200AndApplication()
            throws Exception {

        ApplicationResponse response =
                createResponse(
                        1L,
                        "SmartDeploy Backend",
                        true,
                        false
                );

        when(applicationService.findById(1L))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/applications/1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name")
                        .value("SmartDeploy Backend"))
                .andExpect(jsonPath("$.active")
                        .value(true));
    }

    /* ==========================================================
     *                  SEARCH AND PAGINATION
     * ==========================================================
     */

    @Test
    void searchApplications_shouldReturnPagedApplications()
            throws Exception {

        ApplicationResponse application1 =
                createResponse(
                        1L,
                        "Backend",
                        true,
                        false
                );

        ApplicationResponse application2 =
                createResponse(
                        2L,
                        "Frontend",
                        true,
                        false
                );

        ApplicationPageResponse pageResponse =
                new ApplicationPageResponse(
                        List.of(
                                application1,
                                application2
                        ),
                        0,
                        10,
                        2,
                        1,
                        true,
                        true
                );

        when(applicationService.searchApplications(
                0,
                10,
                null,
                null,
                null
        )).thenReturn(pageResponse);

        mockMvc.perform(
                        get("/api/applications")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.content[0].name")
                                .value("Backend")
                )
                .andExpect(
                        jsonPath("$.content[1].name")
                                .value("Frontend")
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(1)
                );
    }

    /* ==========================================================
     *                  UPDATE APPLICATION
     * ==========================================================
     */

    @Test
    void update_shouldReturn200AndUpdatedApplication()
            throws Exception {

        UpdateApplicationRequest request =
                new UpdateApplicationRequest(
                        "Updated Backend",
                        "Updated description",
                        "https://github.com/company/updated",
                        Technology.NODE_JS
                );

        ApplicationResponse response =
                new ApplicationResponse(
                        1L,
                        "Updated Backend",
                        "Updated description",
                        "https://github.com/company/updated",
                        Technology.NODE_JS,
                        true,
                        false,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                );

        when(applicationService.update(1L, request))
                .thenReturn(response);

        mockMvc.perform(
                        put("/api/applications/1")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "name": "Updated Backend",
                                          "description": "Updated description",
                                          "repositoryUrl": "https://github.com/company/updated",
                                          "technology": "NODE_JS"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name")
                        .value("Updated Backend"))
                .andExpect(jsonPath("$.technology")
                        .value("NODE_JS"));
    }

    /* ==========================================================
     *                  STATUS MANAGEMENT
     * ==========================================================
     */

    @Test
    void deactivate_shouldReturn200AndInactiveApplication()
            throws Exception {

        ApplicationResponse response =
                createResponse(
                        1L,
                        "SmartDeploy",
                        false,
                        false
                );

        when(applicationService.deactivate(1L))
                .thenReturn(response);

        mockMvc.perform(
                        patch(
                                "/api/applications/1/deactivate"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active")
                        .value(false));
    }

    @Test
    void activate_shouldReturn200AndActiveApplication()
            throws Exception {

        ApplicationResponse response =
                createResponse(
                        1L,
                        "SmartDeploy",
                        true,
                        false
                );

        when(applicationService.activate(1L))
                .thenReturn(response);

        mockMvc.perform(
                        patch(
                                "/api/applications/1/activate"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active")
                        .value(true));
    }

    /* ==========================================================
     *                  SOFT DELETE
     * ==========================================================
     */

    @Test
    void softDelete_shouldReturn204NoContent()
            throws Exception {

        doNothing()
                .when(applicationService)
                .softDelete(1L);

        mockMvc.perform(
                        delete("/api/applications/1")
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(applicationService)
                .softDelete(1L);
    }

    /* ==========================================================
     *                  RESTORE
     * ==========================================================
     */

    @Test
    void restore_shouldReturn200AndRestoredApplication()
            throws Exception {

        ApplicationResponse response =
                createResponse(
                        1L,
                        "SmartDeploy",
                        true,
                        false
                );

        when(applicationService.restore(1L))
                .thenReturn(response);

        mockMvc.perform(
                        patch(
                                "/api/applications/1/restore"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active")
                        .value(true))
                .andExpect(jsonPath("$.deleted")
                        .value(false));
    }

    /* ==========================================================
     *                  TEST HELPERS
     * ==========================================================
     */

    private ApplicationResponse createResponse(
            Long id,
            String name,
            boolean active,
            boolean deleted
    ) {
        LocalDateTime now = LocalDateTime.now();

        return new ApplicationResponse(
                id,
                name,
                "Application description",
                "https://github.com/company/application",
                Technology.SPRING_BOOT,
                active,
                deleted,
                now,
                now
        );
    }
}