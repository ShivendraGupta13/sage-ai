package com.company.sage.chat;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.SageProperties;
import com.company.sage.merge.ResultMerger;
import com.company.sage.model.AskRequest;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SageAskServiceTest {

    @Mock
    private GraphRagClient graphRagClient;

    @Mock
    private ResultMerger resultMerger;

    @Mock
    private BaseLlm adkLlm;

    private SageProperties properties;
    private SageAskService askService;

    @BeforeEach
    void setUp() {
        properties = new SageProperties(
                new SageProperties.GraphRag("http://localhost:8000"),
                new SageProperties.Adk(new SageProperties.Adk.Llm("http://localhost:11434", "llama3.2:3b")),
                new SageProperties.Retrieval(5, 0.60),
                new SageProperties.Scoring(0.6, 0.4, 0.1, 0.60)
        );
    }

    @Test
    void shouldProcessAskWithFallbackWhenLlmIsNull() {
        askService = new SageAskService(graphRagClient, resultMerger, properties, null);
        AskRequest request = new AskRequest("How did we solve spot instance termination in AWS?");

        SseEmitter emitter = askService.processAsk(request, "corr-123");
        assertThat(emitter).isNotNull();
    }

    @Test
    void shouldProcessAskWithLlmInterpretationWhenLlmIsProvided() {
        String llmJsonResponse = """
            {
              "problemStatement": "Preventing data loss from AWS EC2 spot instance termination",
              "techNeeded": ["AWS", "EC2", "Kafka"]
            }
            """;

        Content content = Content.builder()
                .parts(List.of(Part.fromText(llmJsonResponse)))
                .build();
        LlmResponse llmResponse = LlmResponse.builder()
                .content(content)
                .build();

        when(adkLlm.generateContent(any(LlmRequest.class), anyBoolean()))
                .thenReturn(Flowable.just(llmResponse));

        askService = new SageAskService(graphRagClient, resultMerger, properties, adkLlm);
        AskRequest request = new AskRequest("How did we solve spot instance termination in AWS?");

        SseEmitter emitter = askService.processAsk(request, "corr-123");
        assertThat(emitter).isNotNull();
    }

    @Test
    void shouldProcessAskWithLlmInterpretationAndSynthesisWhenHitsReturned() {
        String llmJsonResponse = """
            {
              "problemStatement": "Preventing data loss from AWS EC2 spot instance termination",
              "techNeeded": ["AWS", "EC2", "Kafka"]
            }
            """;

        Content content1 = Content.builder()
                .parts(List.of(Part.fromText(llmJsonResponse)))
                .build();
        LlmResponse llmResponse1 = LlmResponse.builder()
                .content(content1)
                .build();

        Content content2 = Content.builder()
                .parts(List.of(Part.fromText("The Platform Team solved spot instance data loss by introducing Kafka checkpointing prior to termination signals.")))
                .build();
        LlmResponse llmResponse2 = LlmResponse.builder()
                .content(content2)
                .build();

        when(adkLlm.generateContent(any(LlmRequest.class), anyBoolean()))
                .thenReturn(Flowable.just(llmResponse1))
                .thenReturn(Flowable.just(llmResponse2));

        com.company.sage.model.CardResult hit = new com.company.sage.model.CardResult(
                1, 0.85, List.of("semantic", "graph"), "Core Platform", "EC2 Spot Loss",
                "HARD_PROBLEMS", List.of("Alice Smith"), "Kafka checkpointing introduced to save state",
                "http://doc", "Evidence", List.of("Orion API")
        );
        when(resultMerger.merge(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(hit));

        askService = new SageAskService(graphRagClient, resultMerger, properties, adkLlm);
        AskRequest request = new AskRequest("How did we solve spot instance termination in AWS?");

        SseEmitter emitter = askService.processAsk(request, "corr-123");
        assertThat(emitter).isNotNull();
    }

    @Test
    void shouldFallbackGracefullyWhenLlmThrowsException() {
        when(adkLlm.generateContent(any(LlmRequest.class), anyBoolean()))
                .thenReturn(Flowable.error(new RuntimeException("Connection refused to Ollama")));

        askService = new SageAskService(graphRagClient, resultMerger, properties, adkLlm);
        AskRequest request = new AskRequest("How did we solve spot instance termination in AWS?");

        SseEmitter emitter = askService.processAsk(request, "corr-123");
        assertThat(emitter).isNotNull();
    }
}
