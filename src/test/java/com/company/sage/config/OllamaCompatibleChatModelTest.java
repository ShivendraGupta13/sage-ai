package com.company.sage.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OllamaCompatibleChatModelTest {

    @Test
    void flattensMultiTextUserMessageBeforeDelegating() {
        AtomicReference<List<ChatMessage>> captured = new AtomicReference<>();
        ChatModel delegate = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                captured.set(new ArrayList<>(request.messages()));
                return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
            }
        };

        ChatModel wrapper = new OllamaCompatibleChatModel(delegate);
        UserMessage multiPart = UserMessage.from(
                TextContent.from("For context:"),
                TextContent.from("[QueryInterpret] said: {\"problemStatement\":\"x\"}"));

        wrapper.chat(ChatRequest.builder().messages(multiPart).build());

        ChatMessage sent = captured.get().get(0);
        assertThat(sent).isInstanceOf(UserMessage.class);
        UserMessage userMessage = (UserMessage) sent;
        assertThat(userMessage.contents()).hasSize(1);
        assertThat(userMessage.singleText())
                .contains("For context:")
                .contains("[QueryInterpret] said:");
    }

    @Test
    void leavesSingleTextMessageUnchanged() {
        UserMessage single = UserMessage.from("hello");
        ChatMessage result = OllamaCompatibleChatModel.flattenUserMessage(single);
        assertThat(result).isSameAs(single);
    }
}
