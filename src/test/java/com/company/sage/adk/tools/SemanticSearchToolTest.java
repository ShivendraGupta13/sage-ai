package com.company.sage.adk.tools;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.SageProperties;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
import com.company.sage.model.SemanticRetrieveRequest;
import com.google.adk.sessions.State;
import com.google.adk.tools.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticSearchToolTest {

    @Mock
    private GraphRagClient graphRagClient;

    @Mock
    private ToolContext toolContext;

    private SageProperties properties;
    private State state;
    private SemanticSearchTool tool;

    @BeforeEach
    void setUp() {
        properties = new SageProperties(
            new SageProperties.GraphRag("http://localhost:8000"),
            new SageProperties.Adk(new SageProperties.Adk.Llm("http://localhost:11434", "llama3.2", 0.0)),
            new SageProperties.Retrieval(5, 0.60),
            new SageProperties.Scoring(0.6, 0.4, 0.1, 0.60)
        );

        state = new State(new HashMap<>());
        tool = new SemanticSearchTool(graphRagClient, properties);
    }

    @Test
    void shouldCallGraphRagClientWithCorrectParametersAndWriteState() {
        when(toolContext.state()).thenReturn(state);
        state.put("correlationId", "test-corr-123");

        RetrieveHit sampleHit = new RetrieveHit("DOC-1", "Title 1", 0.85, 0.0, List.of("semantic"), "Passage text", "Source A", null);
        when(graphRagClient.retrieveSemantic(any(), eq("test-corr-123")))
            .thenReturn(new RetrieveResponse(List.of(sampleHit), 12L, 1));

        List<RetrieveHit> results = tool.semanticSearch("Safely loading images", toolContext);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).docId()).isEqualTo("DOC-1");

        ArgumentCaptor<SemanticRetrieveRequest> requestCaptor = ArgumentCaptor.forClass(SemanticRetrieveRequest.class);
        verify(graphRagClient).retrieveSemantic(requestCaptor.capture(), eq("test-corr-123"));

        SemanticRetrieveRequest req = requestCaptor.getValue();
        assertThat(req.problemStatement()).isEqualTo("Safely loading images");
        assertThat(req.topK()).isEqualTo(5);
        assertThat(req.minScore()).isEqualTo(0.60);
        assertThat(req.useLlm()).isFalse();

        assertThat(state.get("semantic_hits")).isNotNull();
    }

    @Test
    void shouldFallbackToQueryInterpretationInStateWhenArgumentIsBlank() {
        when(toolContext.state()).thenReturn(state);
        state.put("query_interpretation", "{\"problemStatement\":\"SSRF image proxy fix\",\"techNeeded\":[\"SSRF\"]}");

        RetrieveHit sampleHit = new RetrieveHit("DOC-1", "Title 1", 0.85, 0.0, List.of("semantic"), "Passage text", "Source A", null);
        when(graphRagClient.retrieveSemantic(any(), any()))
            .thenReturn(new RetrieveResponse(List.of(sampleHit), 12L, 1));

        List<RetrieveHit> results = tool.semanticSearch("", toolContext);

        assertThat(results).hasSize(1);

        ArgumentCaptor<SemanticRetrieveRequest> requestCaptor = ArgumentCaptor.forClass(SemanticRetrieveRequest.class);
        verify(graphRagClient).retrieveSemantic(requestCaptor.capture(), any());

        assertThat(requestCaptor.getValue().problemStatement()).isEqualTo("SSRF image proxy fix");
    }

    @Test
    void shouldHandleFailSoftEmptyHitsGracefully() {
        when(toolContext.state()).thenReturn(state);
        when(graphRagClient.retrieveSemantic(any(), any()))
            .thenReturn(new RetrieveResponse(List.of(), 0L, 0));

        List<RetrieveHit> results = tool.semanticSearch("Vague problem", toolContext);

        assertThat(results).isEmpty();
        assertThat(state.get("semantic_hits")).isEqualTo(List.of());
    }
}
