package com.company.sage.adk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.sage.adk.tools.GraphTraversalTool;
import com.company.sage.adk.tools.SemanticSearchTool;
import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.RetrievalProperties;
import com.company.sage.config.SageAgents;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionKey;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParallelRetrieveTest {

    private GraphRagClient graphRagClient;
    private RetrievalProperties retrievalProperties;
    private SemanticSearchTool semanticTool;
    private GraphTraversalTool graphTool;

    @BeforeEach
    void setUp() {
        graphRagClient = mock(GraphRagClient.class);
        retrievalProperties = new RetrievalProperties();
        retrievalProperties.setTopK(5);
        retrievalProperties.setMinScore(0.60);

        semanticTool = new SemanticSearchTool(graphRagClient, retrievalProperties);
        graphTool = new GraphTraversalTool(graphRagClient, retrievalProperties);
    }

    @Test
    void testParallelRetrieveFoldsBothSearches() {
        // Mock downstream client responses
        RetrieveHit hit1 = new RetrieveHit("101", "sem-source", 0.85, null, null, null, "SSRF protection using Spring Security", null);
        RetrieveHit hit2 = new RetrieveHit("202", "graph-source", null, 0.75, null, null, "Graph search matched SSRF proxy rules", null);

        when(graphRagClient.retrieveSemantic(any(), anyString()))
                .thenReturn(new RetrieveResponse(List.of(hit1), 100, 1));
        when(graphRagClient.retrieveGraph(any(), anyString()))
                .thenReturn(new RetrieveResponse(List.of(hit2), 120, 1));

        // Create stub LLM model to handle tool fanning execution calls
        BaseLlm fakeLlm = new BaseLlm("fake-model") {
            @Override
            public io.reactivex.rxjava3.core.Flowable<LlmResponse> generateContent(
                    com.google.adk.models.LlmRequest request, boolean stream) {

                Part responsePart;
                String systemInstruction = request.getFirstSystemInstruction().orElse("");

                if (systemInstruction.contains("SemanticSearchAgent")) {
                    boolean hasResponse = request.contents().stream()
                            .filter(c -> c.parts().isPresent())
                            .flatMap(c -> c.parts().get().stream())
                            .anyMatch(p -> p.functionResponse().isPresent());
                    if (!hasResponse) {
                        responsePart = Part.fromFunctionCall(
                                "semanticSearch", Map.of("problemStatement", "SSRF protection"));
                    } else {
                        responsePart = Part.fromText("Semantic hits found");
                    }
                } else if (systemInstruction.contains("GraphTraversalAgent")) {
                    boolean hasResponse = request.contents().stream()
                            .filter(c -> c.parts().isPresent())
                            .flatMap(c -> c.parts().get().stream())
                            .anyMatch(p -> p.functionResponse().isPresent());
                    if (!hasResponse) {
                        responsePart = Part.fromFunctionCall(
                                "graphTraversal", Map.of("techNeeded", List.of("Java")));
                    } else {
                        responsePart = Part.fromText("Graph hits found");
                    }
                } else {
                    responsePart = Part.fromText("Done");
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

        // Create agents using our configuration
        SageAgents sageAgents = new SageAgents();
        LlmAgent semanticSearchAgent = sageAgents.semanticSearchAgent(fakeLlm, semanticTool);
        LlmAgent graphTraversalAgent = sageAgents.graphTraversalAgent(fakeLlm, graphTool);
        ParallelAgent parallelRetrieve = sageAgents.parallelRetrieve(semanticSearchAgent, graphTraversalAgent);

        // Run the agent using InMemoryRunner with appName = "sage"
        InMemoryRunner runner = new InMemoryRunner(parallelRetrieve, "sage");
        
        // Define correlation ID as the session ID
        String correlationId = "corr-123-uuid-456";
        SessionKey sessionKey = new SessionKey("sage", "test-user", correlationId);

        // Pre-populate session state with the QueryInterpret output
        Map<String, Object> initialState = new HashMap<>();
        Map<String, Object> queryInterpretation = new HashMap<>();
        queryInterpretation.put("problemStatement", "SSRF protection");
        queryInterpretation.put("techNeeded", List.of("Java"));
        initialState.put("query_interpretation", queryInterpretation);

        runner.sessionService().createSession(sessionKey, initialState).blockingGet();

        // Run ParallelRetrieve fanning execution
        Content userPrompt = Content.fromParts(Part.fromText("Retrieve knowledge..."));
        List<Event> events = runner.runAsync(sessionKey, userPrompt).toList().blockingGet();
        assertThat(events).isNotEmpty();

        // Assert session states are correctly populated by each fanned agent
        Session session = runner.sessionService().getSession(sessionKey, GetSessionConfig.builder().build()).blockingGet();
        assertThat(session).isNotNull();

        Object semanticHits = session.state().get("semantic_hits");
        Object graphHits = session.state().get("graph_hits");

        assertThat(semanticHits).isNotNull();
        assertThat(graphHits).isNotNull();

        System.out.println("Semantic hits output: " + semanticHits);
        System.out.println("Graph hits output: " + graphHits);
    }
}
