package com.company.sage.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.model.AskRequest;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * End-to-end integration test for the Ask controller under full Spring context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AskIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private AskController askController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private GraphRagClient graphRagClient; // Mock injected from TestConfig

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(askController).build();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public GraphRagClient mockGraphRagClient() {
            return mock(GraphRagClient.class);
        }

        @Bean
        @Primary
        public BaseLlm testBaseLlm() {
            return new BaseLlm("test-model") {
                @Override
                public io.reactivex.rxjava3.core.Flowable<LlmResponse> generateContent(
                        com.google.adk.models.LlmRequest request, boolean stream) {

                    Part responsePart;
                    String systemInstruction = request.getFirstSystemInstruction().orElse("");

                    if (systemInstruction.contains("QueryInterpret")) {
                        responsePart = Part.fromText("""
                                {
                                  "problemStatement": "Implement SSRF protection on proxy endpoints",
                                  "techNeeded": ["Java", "Spring Boot", "Proxy"]
                                }
                                """);
                    } else if (systemInstruction.contains("SemanticSearchAgent")) {
                        boolean hasResponse = request.contents().stream()
                                .filter(c -> c.parts().isPresent())
                                .flatMap(c -> c.parts().get().stream())
                                .anyMatch(p -> p.functionResponse().isPresent());
                        if (!hasResponse) {
                            responsePart = Part.fromFunctionCall("semanticSearch", Map.of("problemStatement", "Protect proxy"));
                        } else {
                            responsePart = Part.fromText("Semantic search completed");
                        }
                    } else if (systemInstruction.contains("GraphTraversalAgent")) {
                        boolean hasResponse = request.contents().stream()
                                .filter(c -> c.parts().isPresent())
                                .flatMap(c -> c.parts().get().stream())
                                .anyMatch(p -> p.functionResponse().isPresent());
                        if (!hasResponse) {
                            responsePart = Part.fromFunctionCall("graphTraversal", Map.of("techNeeded", List.of("Java")));
                        } else {
                            responsePart = Part.fromText("Graph search completed");
                        }
                    } else if (systemInstruction.contains("ResultMerger")) {
                        boolean hasResponse = request.contents().stream()
                                .filter(c -> c.parts().isPresent())
                                .flatMap(c -> c.parts().get().stream())
                                .anyMatch(p -> p.functionResponse().isPresent());
                        if (!hasResponse) {
                            responsePart = Part.fromFunctionCall("merge", Map.of());
                        } else {
                            responsePart = Part.fromText("Merging completed");
                        }
                    } else if (systemInstruction.contains("KnowledgeCardSynth")) {
                        responsePart = Part.fromText("""
                                {
                                  "query": "How to protect against SSRF?",
                                  "problemStatement": "Implement SSRF protection on proxy endpoints",
                                  "techNeeded": ["Java", "Spring Boot", "Proxy"],
                                  "directAnswer": "Implement Spring Security proxy filter rules.",
                                  "results": [],
                                  "gapFlag": false,
                                  "gapMessage": null
                                }
                                """);
                    } else {
                        responsePart = Part.fromText("Completed");
                    }

                    Content content = Content.fromParts(responsePart);
                    LlmResponse res = LlmResponse.builder()
                            .content(content)
                            .build();
                    return io.reactivex.rxjava3.core.Flowable.just(res);
                }

                @Override
                public com.google.adk.models.BaseLlmConnection connect(com.google.adk.models.LlmRequest request) {
                    return null;
                }
            };
        }
    }

    @Test
    void testAskEndpointIntegrationSseSuccess() throws Exception {
        // Mock downstream client responses
        RetrieveHit hit1 = new RetrieveHit("101", "sem-source", 0.85, null, null, null, "SSRF passage", null);
        when(graphRagClient.retrieveSemantic(any(), anyString()))
                .thenReturn(new RetrieveResponse(List.of(hit1), 100, 1));
        when(graphRagClient.retrieveGraph(any(), anyString()))
                .thenReturn(new RetrieveResponse(List.of(), 100, 0));

        AskRequest askRequest = new AskRequest("How to protect against SSRF?");

        MvcResult result = mockMvc.perform(post("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andReturn();

        // Dispatch async stream
        MvcResult asyncResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn();

        String sseOutput = asyncResult.getResponse().getContentAsString();
        System.out.println("SSE Integration Stream output:\n" + sseOutput);

        assertThat(sseOutput)
                .contains("event:status")
                .contains("event:result")
                .contains("event:done")
                .contains("Identified problem state and tech context")
                .contains("directAnswer");
    }
}
