package com.company.sage.config;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.langchain4j.LangChain4j;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class defining beans for LangChain4j and the Google ADK LLM wrapper.
 */
@Configuration
public class LlmConfig {

    @Bean
    public ChatModel langchain4jChatModel(LlmProperties properties) {
        ChatModel ollamaChatModel = OllamaChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .timeout(Duration.ofSeconds(60))
                .build();
        return new CollapsingChatModel(ollamaChatModel);
    }

    @Bean
    public BaseLlm adkModel(ChatModel langchain4jChatModel, LlmProperties properties) {
        return LangChain4j.builder()
                .chatModel(langchain4jChatModel)
                .modelName(properties.getModelName())
                .build();
    }

    /**
     * Decorator class that merges multi-part text messages in LangChain4j.
     */
    private static class CollapsingChatModel implements ChatModel {
        private final ChatModel delegate;

        public CollapsingChatModel(ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            return delegate.chat(collapseRequest(request));
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return delegate.doChat(collapseRequest(request));
        }

        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            return delegate.chat(collapseMessages(messages));
        }

        @Override
        public ChatResponse chat(ChatMessage... messages) {
            return delegate.chat(collapseMessages(Arrays.asList(messages)));
        }

        @Override
        public String chat(String message) {
            return delegate.chat(message);
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return delegate.defaultRequestParameters();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return delegate.listeners();
        }

        @Override
        public ModelProvider provider() {
            return delegate.provider();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return delegate.supportedCapabilities();
        }

        private ChatRequest collapseRequest(ChatRequest request) {
            return request.toBuilder()
                    .messages(collapseMessages(request.messages()))
                    .build();
        }

        private List<ChatMessage> collapseMessages(List<ChatMessage> messages) {
            if (messages == null) {
                return null;
            }
            return messages.stream()
                    .map(message -> {
                        if (message instanceof UserMessage userMessage && userMessage.contents().size() > 1) {
                            String collapsedText = userMessage.contents().stream()
                                    .filter(content -> content instanceof TextContent)
                                    .map(content -> ((TextContent) content).text())
                                    .collect(Collectors.joining("\n"));
                            return UserMessage.from(collapsedText);
                        }
                        return message;
                    })
                    .collect(Collectors.toList());
        }
    }
}
