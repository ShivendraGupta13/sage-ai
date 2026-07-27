package com.company.sage.adk.tools;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.SageProperties;
import com.company.sage.model.GraphRetrieveRequest;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
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
class GraphTraversalToolTest {

    @Mock
    private GraphRagClient graphRagClient;

    @Mock
    private ToolContext toolContext;

    private SageProperties properties;
    private State state;
    private GraphTraversalTool tool;

    @BeforeEach
    void setUp() {
        properties = new SageProperties(
            new SageProperties.GraphRag("http://localhost:8000"),
            new SageProperties.Adk(new SageProperties.Adk.Llm("http://localhost:11434", "llama3.2")),
            new SageProperties.Retrieval(5, 0.60),
            new SageProperties.Scoring(0.6, 0.4, 0.1, 0.60)
        );

        state = new State(new HashMap<>());
        tool = new GraphTraversalTool(graphRagClient, properties);
    }

    @Test
    void shouldCallGraphRagClientWithCorrectParametersAndWriteState() {
        when(toolContext.state()).thenReturn(state);
        state.put("correlationId", "test-corr-456");

        RetrieveHit sampleHit = new RetrieveHit("DOC-2", "Title 2", 0.0, 0.90, List.of("graph"), "Graph passage", "Source B", null);
        when(graphRagClient.retrieveGraph(any(), eq("test-corr-456")))
            .thenReturn(new RetrieveResponse(List.of(sampleHit), 8L, 1));

        List<RetrieveHit> results = tool.graphTraversal(List.of("SSRF", "npm"), toolContext);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).docId()).isEqualTo("DOC-2");

        ArgumentCaptor<GraphRetrieveRequest> requestCaptor = ArgumentCaptor.forClass(GraphRetrieveRequest.class);
        verify(graphRagClient).retrieveGraph(requestCaptor.capture(), eq("test-corr-456"));

        GraphRetrieveRequest req = requestCaptor.getValue();
        assertThat(req.techNeeded()).containsExactly("SSRF", "npm");
        assertThat(req.topK()).isEqualTo(5);

        assertThat(state.get("graph_hits")).isNotNull();
    }

    @Test
    void shouldFallbackToQueryInterpretationInStateWhenArgumentIsEmpty() {
        when(toolContext.state()).thenReturn(state);
        state.put("query_interpretation", "{\"problemStatement\":\"SSRF image proxy fix\",\"techNeeded\":[\"SSRF\", \"npm\"]}");

        RetrieveHit sampleHit = new RetrieveHit("DOC-2", "Title 2", 0.0, 0.90, List.of("graph"), "Graph passage", "Source B", null);
        when(graphRagClient.retrieveGraph(any(), any()))
            .thenReturn(new RetrieveResponse(List.of(sampleHit), 8L, 1));

        List<RetrieveHit> results = tool.graphTraversal(List.of(), toolContext);

        assertThat(results).hasSize(1);

        ArgumentCaptor<GraphRetrieveRequest> requestCaptor = ArgumentCaptor.forClass(GraphRetrieveRequest.class);
        verify(graphRagClient).retrieveGraph(requestCaptor.capture(), any());

        assertThat(requestCaptor.getValue().techNeeded()).containsExactly("SSRF", "npm");
    }

    @Test
    void shouldHandleFailSoftEmptyHitsGracefully() {
        when(toolContext.state()).thenReturn(state);
        when(graphRagClient.retrieveGraph(any(), any()))
            .thenReturn(new RetrieveResponse(List.of(), 0L, 0));

        List<RetrieveHit> results = tool.graphTraversal(List.of("UnknownTech"), toolContext);

        assertThat(results).isEmpty();
        assertThat(state.get("graph_hits")).isEqualTo(List.of());
    }
}
