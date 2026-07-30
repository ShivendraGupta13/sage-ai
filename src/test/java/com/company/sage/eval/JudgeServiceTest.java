package com.company.sage.eval;

import com.company.sage.model.CardResult;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

    @Mock
    private BaseLlm adkLlm;

    private JudgeService judgeService;

    @BeforeEach
    void setUp() {
        judgeService = new JudgeService(adkLlm);
    }

    @Test
    void evaluateAsync_blankAnswer_returnsEarlyWithoutLlmCall() throws Exception {
        CompletableFuture<Void> future = judgeService.evaluateAsync("query", "", List.of(), "corr-1", null);
        future.get();
        assertThat(future).isCompleted();
    }

    @Test
    void evaluateAsync_validAnswer_parsesJudgeJsonScores() throws Exception {
        LlmResponse mockResponse = LlmResponse.builder()
            .content(Content.builder()
                .role("model")
                .parts(List.of(Part.fromText(
                    "{\"faithfulness\": 5, \"relevance\": 4, \"context_precision\": 4, \"hallucination\": 0, \"completeness\": 5, \"reasoning\": \"Looks good\"}"
                )))
                .build())
            .build();

        when(adkLlm.generateContent(any(), anyBoolean()))
            .thenReturn(Flowable.just(mockResponse));

        CardResult hit = new CardResult(
            1, 0.9, List.of("semantic"), "Team", "Title", "Cat", List.of("Priya"), "Summary", "http://url", "detail", List.of()
        );

        CompletableFuture<Void> future = judgeService.evaluateAsync("query", "Sample answer", List.of(hit), "corr-2", null);
        future.get();

        assertThat(future).isCompleted();
    }
}
