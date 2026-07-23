package com.company.sage.adk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.sage.adk.tools.GraphTraversalTool;
import com.company.sage.adk.tools.ResultMergerTool;
import com.company.sage.adk.tools.SemanticSearchTool;
import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.RetrievalProperties;
import com.company.sage.config.SageAgents;
import com.company.sage.config.ScoringProperties;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionKey;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SageRootTest {

    private GraphRagClient graphRagClient;
    private RetrievalProperties retrievalProperties;
    private ScoringProperties scoringProperties;

    private SemanticSearchTool semanticTool;
    private GraphTraversalTool graphTool;
    private ResultMergerTool mergerTool;

    @BeforeEach
    void setUp() {
        graphRagClient = mock(GraphRagClient.class);
        retrievalProperties = new RetrievalProperties();
        retrievalProperties.setTopK(5);
        retrievalProperties.setMinScore(0.60);

        scoringProperties = new ScoringProperties();
        scoringProperties.setW1(0.6);
        scoringProperties.setW2(0.4);
        scoringProperties.setDualMatchBoost(0.1);
        scoringProperties.setMinScore(0.60);

        semanticTool = new SemanticSearchTool(graphRagClient, retrievalProperties);
        graphTool = new GraphTraversalTool(graphRagClient, retrievalProperties);
        mergerTool = new ResultMergerTool(scoringProperties, retrievalProperties);
    }

    @Test
    void testSageRootPipelineEndToEnd() {
        // Mock downstream client responses
        RetrieveHit hit1 = new RetrieveHit("101", "sem-source", 0.85, null, null, null, "SSRF protection using Spring Security", null);
        RetrieveHit hit2 = new RetrieveHit("202", "graph-source", null, 0.75, null, null, "Graph search matched SSRF proxy rules", null);

        when(graphRagClient.retrieveSemantic(any(), anyString()))
                .thenReturn(new RetrieveResponse(List.of(hit1), 100, 1));
        when(graphRagClient.retrieveGraph(any(), anyString()))
                .thenReturn(new RetrieveResponse(List.of(hit2), 120, 1));

        // Create stub LLM model to handle all sequence agent execution calls
        BaseLlm fakeLlm = new BaseLlm("fake-model") {
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
                        responsePart = Part.fromFunctionCall(
                                "semanticSearch", Map.of("problemStatement", "Implement SSRF protection on proxy endpoints"));
                    } else {
                        responsePart = Part.fromText("Semantic search completed");
                    }
                } else if (systemInstruction.contains("GraphTraversalAgent")) {
                    boolean hasResponse = request.contents().stream()
                            .filter(c -> c.parts().isPresent())
                            .flatMap(c -> c.parts().get().stream())
                            .anyMatch(p -> p.functionResponse().isPresent());
                    if (!hasResponse) {
                        responsePart = Part.fromFunctionCall(
                                "graphTraversal", Map.of("techNeeded", List.of("Java", "Spring Boot", "Proxy")));
                    } else {
                        responsePart = Part.fromText("Graph search completed");
                    }
                } else if (systemInstruction.contains("ResultMerger")) {
                    boolean hasResponse = request.contents().stream()
                            .filter(c -> c.parts().isPresent())
                            .flatMap(c -> c.parts().get().stream())
                            .anyMatch(p -> p.functionResponse().isPresent());
                    if (!hasResponse) {
                        Map<String, Object> args = Map.of(
                            "semanticHits", List.of(Map.of("doc_id", "101", "source", "sem-source", "vectorScore", 0.85, "passage", "SSRF protection")),
                            "graphHits", List.of(Map.of("doc_id", "202", "source", "graph-source", "graphScore", 0.75, "passage", "SSRF proxy"))
                        );
                        responsePart = Part.fromFunctionCall("merge", args);
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

        // Assemble sequential agent pipeline
        SageAgents sageAgents = new SageAgents();
        LlmAgent queryInterpret = sageAgents.queryInterpret(fakeLlm);
        LlmAgent semanticSearchAgent = sageAgents.semanticSearchAgent(fakeLlm, semanticTool);
        LlmAgent graphTraversalAgent = sageAgents.graphTraversalAgent(fakeLlm, graphTool);
        ParallelAgent parallelRetrieve = sageAgents.parallelRetrieve(semanticSearchAgent, graphTraversalAgent);
        LlmAgent merger = sageAgents.merger(fakeLlm, mergerTool);
        LlmAgent synth = sageAgents.synth(fakeLlm);

        SequentialAgent sageRoot = sageAgents.sageRootAgent(queryInterpret, parallelRetrieve, merger, synth);

        // Run sequential pipeline in InMemoryRunner
        InMemoryRunner runner = new InMemoryRunner(sageRoot, "sage");
        String correlationId = "corr-789-uuid";
        SessionKey sessionKey = new SessionKey("sage", "test-user", correlationId);

        runner.sessionService().createSession(sessionKey).blockingGet();

        Content userPrompt = Content.fromParts(Part.fromText("How to protect against SSRF?"));
        List<Event> events = runner.runAsync(sessionKey, userPrompt).toList().blockingGet();

        assertThat(events).isNotEmpty();

        // Retrieve and assert sequential execution session outputs
        Session session = runner.sessionService().getSession(sessionKey, GetSessionConfig.builder().build()).blockingGet();
        assertThat(session).isNotNull();

        assertThat(session.state().get("query_interpretation")).isNotNull();
        assertThat(session.state().get("semantic_hits")).isNotNull();
        assertThat(session.state().get("graph_hits")).isNotNull();
        assertThat(session.state().get("merged_hits")).isNotNull();
        assertThat(session.state().get("knowledge_card")).isNotNull();

        System.out.println("End-to-End Pipeline Knowledge Card: " + session.state().get("knowledge_card"));
    }
}
