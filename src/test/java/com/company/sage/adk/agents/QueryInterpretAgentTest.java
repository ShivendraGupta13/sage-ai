package com.company.sage.adk.agents;

import com.company.sage.model.QueryInterpretation;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class QueryInterpretAgentTest {

    @Mock
    private BaseLlm adkLlm;

    @Test
    void shouldBuildQueryInterpretAgentWithCorrectNameAndOutputKey() {
        LlmAgent agent = QueryInterpretAgent.create(adkLlm);

        assertThat(agent).isNotNull();
        assertThat(agent.name()).isEqualTo("QueryInterpret");
        assertThat(agent.outputKey()).contains("query_interpretation");
    }

    @Test
    void shouldParseCleanJsonOutput() {
        String rawJson = """
            {
              "problemStatement": "Safely fetching external images from emails without SSRF exposure",
              "techNeeded": ["SSRF mitigation", "npm image proxy", "email rendering"]
            }
            """;

        QueryInterpretation result = QueryInterpretationParser.parse(rawJson, "Default question");

        assertThat(result).isNotNull();
        assertThat(result.problemStatement()).isEqualTo("Safely fetching external images from emails without SSRF exposure");
        assertThat(result.techNeeded()).containsExactly("SSRF mitigation", "npm image proxy", "email rendering");
    }

    @Test
    void shouldParseMarkdownWrappedJsonOutput() {
        String rawMarkdownJson = """
            ```json
            {
              "problemStatement": "Preventing SSRF in image loader",
              "techNeeded": ["SSRF", "Node.js"]
            }
            ```
            """;

        QueryInterpretation result = QueryInterpretationParser.parse(rawMarkdownJson, "Default question");

        assertThat(result).isNotNull();
        assertThat(result.problemStatement()).isEqualTo("Preventing SSRF in image loader");
        assertThat(result.techNeeded()).containsExactly("SSRF", "Node.js");
    }

    @Test
    void shouldParseConversationalWrappedJsonOutput() {
        String conversationalJson = """
            Here is the requested JSON object:
            {
              "problemStatement": "Preventing SSRF in image loader",
              "techNeeded": ["SSRF", "Node.js"]
            }
            Hope this helps!
            """;

        QueryInterpretation result = QueryInterpretationParser.parse(conversationalJson, "Default question");

        assertThat(result).isNotNull();
        assertThat(result.problemStatement()).isEqualTo("Preventing SSRF in image loader");
        assertThat(result.techNeeded()).containsExactly("SSRF", "Node.js");
    }

    @Test
    void shouldFallbackToOriginalQueryWhenJsonParsingFails() {
        String invalidOutput = "I am sorry, I cannot parse this input into JSON.";

        QueryInterpretation result = QueryInterpretationParser.parse(invalidOutput, "How did we migrate to OpenTelemetry?");

        assertThat(result).isNotNull();
        assertThat(result.problemStatement()).isEqualTo("How did we migrate to OpenTelemetry?");
        assertThat(result.techNeeded()).isEmpty();
    }
}
