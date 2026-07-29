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
    void instructionShouldHandleRawQueriesAndIncludeEcosystemPeers() {
        assertThat(QueryInterpretAgent.INSTRUCTION)
            // Input flexibility
            .contains("any form of input")
            .contains("keyword phrase")
            .contains("raw problem fragment")
            // CRITICAL blocks
            .contains("CRITICAL (Output format)")
            .contains("CRITICAL (No solutions)")
            .contains("never the solution")
            .contains("<generated_problem_statement>")
            // Example of wrong/correct output
            .contains("Example of WRONG output")
            .contains("Example of CORRECT output")
            // problemStatement structure
            .contains("Sentence 1")
            .contains("Sentence 2")
            .contains("Sentence 3")
            .contains("prior work, implementations, or decisions made within the organization")
            // techNeeded rules
            .contains("ecosystem peers or alternatives")
            .contains("Kafka → Pulsar, Kinesis")
            .contains("Elasticsearch → OpenSearch, Solr")
            .contains("Kubernetes → ECS, Nomad")
            .contains("Do not invent unrelated technologies")
            .contains("leave the array empty")
            .contains("Parallel Unit Testing")
            .contains("Blue-Green Deployment")
            // Obsolete strings must not appear
            .doesNotContain("Prefer an empty array over a guess")
            .doesNotContain("Q: How do we stream");
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
