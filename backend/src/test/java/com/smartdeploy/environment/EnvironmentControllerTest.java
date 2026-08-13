package com.smartdeploy.environment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class EnvironmentControllerTest {

    @Mock
    private EnvironmentService environmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EnvironmentController controller =
                new EnvironmentController(environmentService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }
}