package com.company.sage.chat;

import com.company.sage.model.QueryInterpretation;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryInterpreterTest {

    @Mock
    private BaseLlm adkLlm;

    @InjectMocks
    private QueryInterpreter queryInterpreter;

    @Test
    void shouldParseSuccessfulLlmJsonIntoInterpretation() {
        String llmJson = """
            {
              "problemStatement": "Preventing data loss when EC2 spot instances terminate",
              "techNeeded": ["EC2 spot", "checkpointing", "EBS"]
            }
            """;
        stubLlmResponse(llmJson);

        QueryInterpretation result = queryInterpreter.interpret(
            "How to prevent data loss from EC2 spot instance terminations",
            "corr-1"
        );

        assertThat(result.problemStatement())
            .isEqualTo("Preventing data loss when EC2 spot instances terminate");
        assertThat(result.techNeeded()).containsExactly("EC2 spot", "checkpointing", "EBS");

        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(adkLlm).generateContent(requestCaptor.capture(), eq(false));
        LlmRequest sent = requestCaptor.getValue();
        assertThat(sent.getSystemInstructions()).isNotEmpty();
        assertThat(sent.contents()).hasSize(1);
        assertThat(sent.contents().get(0).text())
            .contains("How to prevent data loss from EC2 spot instance terminations");
    }

    @Test
    void shouldFallbackToRawQueryWhenLlmThrows() {
        when(adkLlm.generateContent(any(LlmRequest.class), eq(false)))
            .thenReturn(Flowable.error(new RuntimeException("connection refused")));

        String raw = "How to prevent data loss from EC2 spot instance terminations";
        QueryInterpretation result = queryInterpreter.interpret(raw, "corr-fail");

        assertThat(result.problemStatement()).isEqualTo(raw);
        assertThat(result.techNeeded()).isEmpty();
    }

    @Test
    void shouldFallbackToRawQueryWhenLlmReturnsUnparsableText() {
        stubLlmResponse("I cannot help with that.");

        String raw = "How did we migrate to OpenTelemetry?";
        QueryInterpretation result = queryInterpreter.interpret(raw, "corr-bad");

        assertThat(result.problemStatement()).isEqualTo(raw);
        assertThat(result.techNeeded()).isEmpty();
    }

    @Test
    void shouldReturnEmptyInterpretationForBlankQuery() {
        QueryInterpretation result = queryInterpreter.interpret("   ", "corr-blank");

        assertThat(result.problemStatement()).isEmpty();
        assertThat(result.techNeeded()).isEmpty();
    }

    private void stubLlmResponse(String text) {
        LlmResponse response = LlmResponse.builder()
            .content(Content.builder()
                .role("model")
                .parts(List.of(Part.fromText(text)))
                .build())
            .build();
        when(adkLlm.generateContent(any(LlmRequest.class), eq(false)))
            .thenReturn(Flowable.just(response));
    }
}
