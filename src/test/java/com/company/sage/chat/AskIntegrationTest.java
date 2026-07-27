package com.company.sage.chat;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.model.QueryInterpretation;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AskIntegrationTest {

    @Autowired
    private SpringChatController controller;

    @MockitoBean
    private GraphRagClient graphRagClient;

    @MockitoBean
    private QueryInterpreter queryInterpreter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();

        when(queryInterpreter.interpret(any(), any())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            return new QueryInterpretation(query, List.of());
        });
    }

    @Test
    void shouldExecuteFullAskPipelineAndEmitSseSequence() throws Exception {
        RetrieveHit semHit = new RetrieveHit("DOC-101", "SSRF Fix in Proxy", 1.0, 0.0, List.of("semantic"), "Passage 1", "Source A", null);
        RetrieveHit graphHit = new RetrieveHit("DOC-102", "Node Image Sanitizer", 0.0, 1.0, List.of("graph"), "Passage 2", "Source B", null);

        when(graphRagClient.retrieveSemantic(any(), any()))
            .thenReturn(new RetrieveResponse(List.of(semHit), 10L, 1));
        when(graphRagClient.retrieveGraph(any(), any()))
            .thenReturn(new RetrieveResponse(List.of(graphHit), 8L, 1));

        when(queryInterpreter.interpret(eq("How did we solve SSRF in node services?"), any()))
            .thenReturn(new QueryInterpretation(
                "Preventing SSRF when loading images in Node services",
                List.of("SSRF mitigation", "Node.js", "image proxy")
            ));

        MvcResult result = mockMvc.perform(post("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", "test-correlation-123")
                .content("{\"query\":\"How did we solve SSRF in node services?\"}"))
            .andExpect(status().isOk())
            .andReturn();

        String responseContent = result.getResponse().getContentAsString();

        assertThat(responseContent).contains("event:status");
        assertThat(responseContent).contains("Interpreting query");
        assertThat(responseContent).contains("Identified problem state and tech context");
        assertThat(responseContent).contains("Preventing SSRF when loading images in Node services");
        assertThat(responseContent).contains("SSRF mitigation");
        assertThat(responseContent).contains("Searching knowledge base");
        assertThat(responseContent).contains("Ranking results");
        assertThat(responseContent).contains("event:result");
        assertThat(responseContent).contains("\"confidenceScore\":0.6");
        assertThat(responseContent).contains("\"gapFlag\":false");
        assertThat(responseContent).contains("event:done");
        // Raw query must not be echoed as problemStatement when interpreter succeeds
        assertThat(responseContent).contains("\"query\":\"How did we solve SSRF in node services?\"");
        assertThat(responseContent).doesNotContain(
            "\"problemStatement\":\"How did we solve SSRF in node services?\""
        );
    }

    @Test
    void shouldProduceGapCardWhenRetrievalReturnsNoHits() throws Exception {
        when(graphRagClient.retrieveSemantic(any(), any()))
            .thenReturn(new RetrieveResponse(List.of(), 0L, 0));
        when(graphRagClient.retrieveGraph(any(), any()))
            .thenReturn(new RetrieveResponse(List.of(), 0L, 0));

        MvcResult result = mockMvc.perform(post("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", "test-correlation-gap")
                .content("{\"query\":\"Unseen problem statement\"}"))
            .andExpect(status().isOk())
            .andReturn();

        String responseContent = result.getResponse().getContentAsString();

        assertThat(responseContent).contains("event:status");
        assertThat(responseContent).contains("event:result");
        assertThat(responseContent).contains("\"gapFlag\":true");
        assertThat(responseContent).contains("No internal prior art found");
        assertThat(responseContent).contains("event:done");
    }
}
