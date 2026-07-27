package com.company.sage.chat;

import com.company.sage.clients.graphrag.GraphRagClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GraphRagClient graphRagClient;

    @BeforeEach
    void setUp() {
        HealthController healthController = new HealthController(graphRagClient);
        mockMvc = MockMvcBuilders.standaloneSetup(healthController).build();
    }

    @Test
    void shouldReturnOkStatusWhenGraphRagIsReachable() throws Exception {
        given(graphRagClient.checkHealth()).willReturn(true);

        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.semanticServiceReachable").value(true))
            .andExpect(jsonPath("$.graphServiceReachable").value(true));
    }

    @Test
    void shouldReturnDegradedStatusWhenGraphRagIsUnreachable() throws Exception {
        given(graphRagClient.checkHealth()).willReturn(false);

        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("degraded"))
            .andExpect(jsonPath("$.semanticServiceReachable").value(false))
            .andExpect(jsonPath("$.graphServiceReachable").value(false));
    }
}
