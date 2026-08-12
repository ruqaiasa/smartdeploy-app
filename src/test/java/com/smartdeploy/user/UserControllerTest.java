package com.smartdeploy.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;


@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        UserController userController =
                new UserController(userService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .build();
    }
    @Test
    void findById_shouldReturn200AndUser() throws Exception {

        UserResponse response = new UserResponse(
                1L,
                "Ruqaia",
                "Sabta",
                "ruqaia@smartdeploy.com",
                Role.ADMIN,
                true,
                false,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(userService.findById(1L))
                .thenReturn(response);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Ruqaia"))
                .andExpect(jsonPath("$.lastName").value("Sabta"))
                .andExpect(jsonPath("$.email")
                        .value("ruqaia@smartdeploy.com"))
                .andExpect(jsonPath("$.role")
                        .value("ADMIN"))
                .andExpect(jsonPath("$.enabled")
                        .value(true))
                .andExpect(jsonPath("$.deleted")
                        .value(false));
    }

    @Test
    void searchUsers_shouldReturnPagedUsers() throws Exception {

        UserResponse user1 = new UserResponse(
                1L,
                "Ruqaia",
                "Sabta",
                "ruqaia@smartdeploy.com",
                Role.ADMIN,
                true,
                false,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        UserResponse user2 = new UserResponse(
                2L,
                "Ahmed",
                "Ben Ali",
                "ahmed@smartdeploy.com",
                Role.DEVOPS,
                true,
                false,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        UserPageResponse pageResponse =
                new UserPageResponse(
                        List.of(user1, user2),
                        0,
                        10,
                        2,
                        1,
                        true,
                        true
                );

        when(userService.searchUsers(
                0,
                10,
                null,
                null,
                null
        )).thenReturn(pageResponse);

        mockMvc.perform(
                        get("/api/users")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].email")
                        .value("ruqaia@smartdeploy.com"))
                .andExpect(jsonPath("$.content[1].email")
                        .value("ahmed@smartdeploy.com"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }
    @Test
    void create_shouldReturn201AndCreatedUser() throws Exception {

        CreateUserRequest request = new CreateUserRequest(
                "Ahmed",
                "Ben Ali",
                "ahmed@smartdeploy.com",
                "Password123",
                Role.DEVOPS
        );

        UserResponse response = new UserResponse(
                2L,
                "Ahmed",
                "Ben Ali",
                "ahmed@smartdeploy.com",
                Role.DEVOPS,
                true,
                false,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(userService.create(request))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "firstName":"Ahmed",
                                          "lastName":"Ben Ali",
                                          "email":"ahmed@smartdeploy.com",
                                          "password":"Password123",
                                          "role":"DEVOPS"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.firstName").value("Ahmed"))
                .andExpect(jsonPath("$.lastName").value("Ben Ali"))
                .andExpect(jsonPath("$.email")
                        .value("ahmed@smartdeploy.com"))
                .andExpect(jsonPath("$.role")
                        .value("DEVOPS"))
                .andExpect(jsonPath("$.enabled")
                        .value(true))
                .andExpect(jsonPath("$.deleted")
                        .value(false));
    }


}