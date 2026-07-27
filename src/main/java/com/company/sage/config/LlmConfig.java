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

        OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(60))
            .build();

        return LangChain4j.builder()
            .chatModel(ollamaChatModel)
            .modelName(modelName)
            .build();
    }
}
