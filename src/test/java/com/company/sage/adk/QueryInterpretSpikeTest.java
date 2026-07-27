package com.company.sage.adk;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OLLAMA_SPIKE", matches = "true")
class QueryInterpretSpikeTest {

    @Autowired
    private BaseLlm adkLlm;

    @Test
    void shouldTalkToLocalOllamaWhenSpikeEnabled() {
        assertThat(adkLlm).isNotNull();

        LlmRequest request = LlmRequest.builder()
            .contents(List.of(
                Content.builder()
                    .role("user")
                    .parts(List.of(Part.fromText("Say 'Hello' in JSON format: {\"message\": \"Hello\"}")))
                    .build()
            ))
            .build();

        LlmResponse response = adkLlm.generateContent(request, false).blockingFirst();

        assertThat(response).isNotNull();
        assertThat(response.content()).isPresent();
        assertThat(response.content().map(Content::text)).isPresent();
    }
}
