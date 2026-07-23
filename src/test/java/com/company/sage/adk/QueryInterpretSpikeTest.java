package com.company.sage.adk;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OLLAMA_SPIKE", matches = "true")
class QueryInterpretSpikeTest {

    @Test
    void testOllamaConnectionSpike() {
        ChatModel chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.2:3b")
                .timeout(Duration.ofSeconds(60))
                .build();

        BaseLlm model = LangChain4j.builder()
                .chatModel(chatModel)
                .modelName("llama3.2:3b")
                .build();

        Content content = Content.fromParts(Part.fromText("Say 'ping' and nothing else."));
        LlmRequest request = LlmRequest.builder()
                .contents(List.of(content))
                .build();

        LlmResponse response = model.generateContent(request, false).blockingFirst();

        assertThat(response).isNotNull();
        String reply = response.content()
                .map(Content::text)
                .orElse("");

        System.out.println("Ollama response: " + reply);
        assertThat(reply).containsIgnoringCase("ping");
    }
}
