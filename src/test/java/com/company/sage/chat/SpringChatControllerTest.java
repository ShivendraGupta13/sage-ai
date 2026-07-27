package com.company.sage.chat;

import com.company.sage.model.AskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SpringChatControllerTest {

    @Mock
    private SageAskService askService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SpringChatController controller = new SpringChatController(askService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void shouldReturn400BadRequestWhenQueryIsBlank() throws Exception {
        mockMvc.perform(post("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.errors[0].field").value("query"));
    }

    @Test
    void shouldReturn400BadRequestWhenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/ask")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldReturn200OkWithSseEmitterForValidRequest() throws Exception {
        when(askService.processAsk(any(AskRequest.class), eq("custom-correlation-123")))
            .thenReturn(new SseEmitter());

        mockMvc.perform(post("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", "custom-correlation-123")
                .content("{\"query\":\"How did we solve SSRF in node services?\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Correlation-Id", "custom-correlation-123"));
    }
}
