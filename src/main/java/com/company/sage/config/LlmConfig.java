package com.company.sage.config;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.langchain4j.LangChain4j;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class defining beans for LangChain4j and the Google ADK LLM wrapper.
 */
@Configuration
public class LlmConfig {

    @Bean
    public ChatModel langchain4jChatModel(LlmProperties properties) {
        return OllamaChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public BaseLlm adkModel(ChatModel langchain4jChatModel, LlmProperties properties) {
        return LangChain4j.builder()
                .chatModel(langchain4jChatModel)
                .modelName(properties.getModelName())
                .build();
    }
}
