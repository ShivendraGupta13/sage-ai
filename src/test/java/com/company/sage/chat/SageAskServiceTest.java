package com.company.sage.chat;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.SageProperties;
import com.company.sage.merge.ResultMerger;
import com.company.sage.model.AskRequest;
import com.company.sage.model.QueryInterpretation;
import com.company.sage.model.RetrieveResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SageAskServiceTest {

    @Mock
    private GraphRagClient graphRagClient;

    @Mock
    private ResultMerger resultMerger;

    @Mock
    private QueryInterpreter queryInterpreter;

    private SageAskService askService;

    @BeforeEach
    void setUp() {
        SageProperties properties = new SageProperties(
            new SageProperties.GraphRag("http://localhost:8000"),
            new SageProperties.Adk(new SageProperties.Adk.Llm("http://localhost:11434", "llama3.2")),
            new SageProperties.Retrieval(5, 0.60),
            new SageProperties.Scoring(0.6, 0.4, 0.1, 0.35)
        );
        askService = new SageAskService(graphRagClient, resultMerger, properties, queryInterpreter);
    }

    @Test
    void shouldLaunchSemanticAndGraphConcurrentlyWhenTechNeededNonEmpty() throws Exception {
        when(queryInterpreter.interpret(any(), any()))
            .thenReturn(new QueryInterpretation("Preventing SSRF", List.of("SSRF", "Node.js")));

        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicInteger maxInFlight = new AtomicInteger(0);

        org.mockito.stubbing.Answer<RetrieveResponse> holdAndTrack = inv -> {
            int current = inFlight.incrementAndGet();
            maxInFlight.updateAndGet(m -> Math.max(m, current));
            Thread.sleep(150);
            inFlight.decrementAndGet();
            return new RetrieveResponse(List.of(), 1L, 0);
        };

        when(graphRagClient.retrieveSemantic(any(), any())).thenAnswer(holdAndTrack);
        when(graphRagClient.retrieveGraph(any(), any())).thenAnswer(holdAndTrack);
        when(resultMerger.merge(any(), any(), any(), anyInt())).thenReturn(List.of());

        SseEmitter emitter = askService.processAsk(
            new AskRequest("How did we solve SSRF?"),
            "corr-parallel"
        );

        assertThat(emitter).isNotNull();
        assertThat(maxInFlight.get())
            .as("semantic and graph retrieve should overlap when techNeeded is non-empty")
            .isGreaterThanOrEqualTo(2);
    }
}
