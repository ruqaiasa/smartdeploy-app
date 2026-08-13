package com.smartdeploy.deployment;

import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DeploymentControllerTest {

    @Mock
    private DeploymentService deploymentService;

    private MockMvc mockMvc;

    private User currentUser;

    @BeforeEach
    void setUp() {

        DeploymentController controller =
                new DeploymentController(deploymentService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();

        currentUser = createUser();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        currentUser,
                        null,
                        currentUser.getAuthorities()
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /* ==========================================================
     *                  CREATE DEPLOYMENT
     * ==========================================================
     */

    @Test
    void create_shouldReturn201AndDeployment()
            throws Exception {

        CreateDeploymentRequest request =
                new CreateDeploymentRequest(
                        1L,
                        2L
                );

        DeploymentResponse response =
                createResponse(
                        DeploymentStatus.PENDING,
                        null
                );

        when(deploymentService.create(
                request,
                currentUser
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/deployments")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "versionId": 1,
                                          "environmentId": 2
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(content()
                        .contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        ))
                .andExpect(jsonPath("$.id")
                        .value(10))
                .andExpect(jsonPath("$.applicationName")
                        .value("SmartDeploy Backend"))
                .andExpect(jsonPath("$.version")
                        .value("1.0.0"))
                .andExpect(jsonPath("$.environmentName")
                        .value("Production"))
                .andExpect(jsonPath("$.requestedByEmail")
                        .value("ruqaia@smartdeploy.com"))
                .andExpect(jsonPath("$.status")
                        .value("PENDING"))
                .andExpect(jsonPath("$.deleted")
                        .value(false));

        verify(deploymentService)
                .create(request, currentUser);
    }

    @Test
    void create_shouldReturn400WhenRequestIsInvalid()
            throws Exception {

        mockMvc.perform(
                        post("/api/deployments")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "versionId": null,
                                          "environmentId": null
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deploymentService);
    }

    /* ==========================================================
     *                  FIND DEPLOYMENT
     * ==========================================================
     */

    @Test
    void findById_shouldReturn200AndDeployment()
            throws Exception {

        when(deploymentService.findById(10L))
                .thenReturn(
                        createResponse(
                                DeploymentStatus.SUCCESS,
                                null
                        )
                );

        mockMvc.perform(
                        get("/api/deployments/10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(10))
                .andExpect(jsonPath("$.status")
                        .value("SUCCESS"))
                .andExpect(jsonPath("$.versionId")
                        .value(1))
                .andExpect(jsonPath("$.environmentId")
                        .value(2));
    }

    /* ==========================================================
     *                  SEARCH DEPLOYMENTS
     * ==========================================================
     */

    @Test
    void searchDeployments_shouldReturnPage()
            throws Exception {

        DeploymentResponse deployment =
                createResponse(
                        DeploymentStatus.SUCCESS,
                        null
                );

        DeploymentPageResponse pageResponse =
                new DeploymentPageResponse(
                        List.of(deployment),
                        0,
                        10,
                        1,
                        1,
                        true,
                        true
                );

        when(deploymentService.searchDeployments(
                0,
                10,
                null,
                null,
                null,
                null,
                null
        )).thenReturn(pageResponse);

        mockMvc.perform(
                        get("/api/deployments")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.content[0].status")
                                .value("SUCCESS")
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(1)
                );
    }

    /* ==========================================================
     *                  UPDATE STATUS
     * ==========================================================
     */

    @Test
    void updateStatus_shouldReturnUpdatedDeployment()
            throws Exception {

        UpdateDeploymentStatusRequest request =
                new UpdateDeploymentStatusRequest(
                        DeploymentStatus.RUNNING,
                        null
                );

        when(deploymentService.updateStatus(
                10L,
                request
        )).thenReturn(
                createResponse(
                        DeploymentStatus.RUNNING,
                        null
                )
        );

        mockMvc.perform(
                        patch(
                                "/api/deployments/10/status"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "status": "RUNNING",
                                          "errorMessage": null
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("RUNNING"));

        verify(deploymentService)
                .updateStatus(10L, request);
    }

    @Test
    void updateStatus_shouldReturn400WhenStatusIsMissing()
            throws Exception {

        mockMvc.perform(
                        patch(
                                "/api/deployments/10/status"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "status": null,
                                          "errorMessage": null
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deploymentService);
    }

    /* ==========================================================
     *                  UPDATE METADATA
     * ==========================================================
     */

    @Test
    void updateMetadata_shouldReturnUpdatedMetadata()
            throws Exception {

        UpdateDeploymentMetadataRequest request =
                new UpdateDeploymentMetadataRequest(
                        42,
                        "https://jenkins/job/smartdeploy/42"
                );

        DeploymentResponse response =
                createResponse(
                        DeploymentStatus.RUNNING,
                        null
                );

        response = new DeploymentResponse(
                response.id(),
                response.applicationId(),
                response.applicationName(),
                response.versionId(),
                response.version(),
                response.environmentId(),
                response.environmentName(),
                response.environmentType(),
                response.requestedById(),
                response.requestedByEmail(),
                response.status(),
                response.startedAt(),
                response.finishedAt(),
                response.durationSeconds(),
                42,
                "https://jenkins/job/smartdeploy/42",
                response.errorMessage(),
                response.rollbackOfId(),
                response.deleted(),
                response.createdAt(),
                response.updatedAt()
        );

        when(deploymentService.updateMetadata(
                10L,
                request
        )).thenReturn(response);

        mockMvc.perform(
                        patch(
                                "/api/deployments/10/metadata"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "jenkinsBuildNumber": 42,
                                          "deploymentLogsUrl": "https://jenkins/job/smartdeploy/42"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.jenkinsBuildNumber")
                                .value(42)
                )
                .andExpect(
                        jsonPath("$.deploymentLogsUrl")
                                .value(
                                        "https://jenkins/job/smartdeploy/42"
                                )
                );
    }

    /* ==========================================================
     *                  ROLLBACK
     * ==========================================================
     */

    @Test
    void rollback_shouldReturn201AndRollbackDeployment()
            throws Exception {

        DeploymentResponse response =
                createResponse(
                        DeploymentStatus.PENDING,
                        10L
                );

        when(deploymentService.createRollback(
                10L,
                currentUser
        )).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/deployments/10/rollback"
                        )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status")
                        .value("PENDING"))
                .andExpect(jsonPath("$.rollbackOfId")
                        .value(10));

        verify(deploymentService)
                .createRollback(10L, currentUser);
    }

    /* ==========================================================
     *                  SOFT DELETE
     * ==========================================================
     */

    @Test
    void softDelete_shouldReturn204NoContent()
            throws Exception {

        doNothing()
                .when(deploymentService)
                .softDelete(10L);

        mockMvc.perform(
                        delete("/api/deployments/10")
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(deploymentService)
                .softDelete(10L);
    }

    /* ==========================================================
     *                  TEST HELPERS
     * ==========================================================
     */

    private User createUser() {

        LocalDateTime now = LocalDateTime.now();

        return User.builder()
                .id(3L)
                .firstName("Ruqaia")
                .lastName("Sabta")
                .email("ruqaia@smartdeploy.com")
                .password("encoded-password")
                .role(Role.DEVOPS)
                .enabled(true)
                .deleted(false)
                .tokenVersion(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private DeploymentResponse createResponse(
            DeploymentStatus status,
            Long rollbackOfId
    ) {
        LocalDateTime now = LocalDateTime.now();

        return new DeploymentResponse(
                10L,
                100L,
                "SmartDeploy Backend",
                1L,
                "1.0.0",
                2L,
                "Production",
                "PRODUCTION",
                3L,
                "ruqaia@smartdeploy.com",
                status,
                status == DeploymentStatus.PENDING
                        ? null
                        : now.minusSeconds(30),
                status == DeploymentStatus.SUCCESS
                        || status == DeploymentStatus.FAILED
                        || status == DeploymentStatus.CANCELLED
                        ? now
                        : null,
                status == DeploymentStatus.SUCCESS
                        || status == DeploymentStatus.FAILED
                        || status == DeploymentStatus.CANCELLED
                        ? 30L
                        : null,
                null,
                null,
                status == DeploymentStatus.FAILED
                        ? "Deployment failed"
                        : null,
                rollbackOfId,
                false,
                now,
                now
        );
    }
}