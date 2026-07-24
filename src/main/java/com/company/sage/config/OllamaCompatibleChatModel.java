package com.company.sage.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ollama's LangChain4j adapter requires a single {@link TextContent} per {@link UserMessage}.
 * Google ADK SequentialAgent context injection creates multiple text parts ("For context:",
 * "[Agent] said: …"), which would otherwise fail with
 * {@code Expecting single text content, but got: [...]}.
 *
 * <p>This wrapper concatenates adjacent text parts before delegating to Ollama.
 */
final class OllamaCompatibleChatModel implements ChatModel {

    private final ChatModel delegate;

    OllamaCompatibleChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        List<ChatMessage> flattened = chatRequest.messages().stream()
                .map(OllamaCompatibleChatModel::flattenUserMessage)
                .toList();
        return delegate.chat(chatRequest.toBuilder().messages(flattened).build());
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

    static ChatMessage flattenUserMessage(ChatMessage message) {
        if (!(message instanceof UserMessage userMessage)) {
            return message;
        }

        List<Content> contents = userMessage.contents();
        if (contents == null || contents.size() <= 1) {
            return message;
        }

        long textParts = contents.stream().filter(TextContent.class::isInstance).count();
        if (textParts <= 1) {
            return message;
        }

        String joinedText = contents.stream()
                .filter(TextContent.class::isInstance)
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining("\n"));

        List<Content> rebuilt = new ArrayList<>();
        rebuilt.add(TextContent.from(joinedText));
        contents.stream()
                .filter(c -> !(c instanceof TextContent))
                .forEach(rebuilt::add);

        String name = userMessage.name();
        if (name != null && !name.isBlank()) {
            return UserMessage.from(name, rebuilt);
        }
        return UserMessage.from(rebuilt);
    }
}
