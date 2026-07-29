package com.company.sage.config;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.langchain4j.LangChain4j;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig {

    @Bean
    public BaseLlm adkLlm(SageProperties properties) {
        String baseUrl = properties.adk().llm().baseUrl();
        String modelName = properties.adk().llm().modelName();

        Double temp = (properties.adk() != null && properties.adk().llm() != null)
            ? properties.adk().llm().temperature()
            : null;

        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .maxRetries(0)
            .timeout(Duration.ofSeconds(60));

        if (temp != null) {
            builder.temperature(temp);
        }

        OllamaChatModel ollamaChatModel = builder.build();

        return LangChain4j.builder()
            .chatModel(ollamaChatModel)
            .modelName(modelName)
            .build();
    }
}
