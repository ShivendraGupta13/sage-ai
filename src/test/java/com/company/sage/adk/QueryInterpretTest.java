package com.company.sage.adk;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.sage.config.SageAgents;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionKey;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryInterpretTest {

    @Test
    void testQueryInterpretAgentWithFakeLlm() {
        String jsonResponse = """
                {
                  "problemStatement": "Implement SSRF protection on proxy endpoints",
                  "techNeeded": ["Java", "Spring Boot", "Proxy"]
                }
                """;

        // Define a fake model that stub-returns the expected JSON string
        BaseLlm fakeLlm = new BaseLlm("fake-model") {
            @Override
            public io.reactivex.rxjava3.core.Flowable<LlmResponse> generateContent(
                    com.google.adk.models.LlmRequest request, boolean stream) {
                
                Content content = Content.fromParts(Part.fromText(jsonResponse));
                LlmResponse res = LlmResponse.builder()
                        .content(content)
                        .build();
                return io.reactivex.rxjava3.core.Flowable.just(res);
            }

            @Override
            public com.google.adk.models.BaseLlmConnection connect(com.google.adk.models.LlmRequest request) {
                return null;
            }
        };

        // Create the agent
        SageAgents sageAgents = new SageAgents();
        LlmAgent agent = sageAgents.queryInterpret(fakeLlm);

        // Run the agent using InMemoryRunner and explicitly set the appName to "sage"
        InMemoryRunner runner = new InMemoryRunner(agent, "sage");
        SessionKey sessionKey = new SessionKey("sage", "test-user", "session-123");
        Content userPrompt = Content.fromParts(Part.fromText("How to protect against SSRF?"));

        // Initialize the session in the runner's session service first
        runner.sessionService().createSession(sessionKey).blockingGet();

        // Wait for execution flow to complete
        List<Event> events = runner.runAsync(sessionKey, userPrompt).toList().blockingGet();
        assertThat(events).isNotEmpty();

        // Retrieve session and assert state populated correctly
        GetSessionConfig config = GetSessionConfig.builder().build();
        Session session = runner.sessionService().getSession(sessionKey, config).blockingGet();
        
        assertThat(session).isNotNull();
        Object interpretOutput = session.state().get("query_interpretation");
        assertThat(interpretOutput).isNotNull();

        System.out.println("Session state query_interpretation: " + interpretOutput);
        assertThat(interpretOutput.toString()).contains("SSRF protection");
    }
}
