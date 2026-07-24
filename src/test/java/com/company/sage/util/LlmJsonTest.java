package com.company.sage.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmJsonTest {

    @Test
    void parseMap_stripsJsonFencesAndExtractsFields() throws Exception {
        String fenced = """
                ```json
                {
                  "problemStatement": "Migrating to OpenTelemetry",
                  "techNeeded": ["OpenTelemetry", "Telemetry Migration"]
                }
                ```
                """;

        Map<String, Object> map = LlmJson.parseMap(fenced);

        assertThat(map.get("problemStatement")).isEqualTo("Migrating to OpenTelemetry");
        assertThat(map.get("techNeeded")).isEqualTo(List.of("OpenTelemetry", "Telemetry Migration"));
    }

    @Test
    void parseMap_acceptsRawJson() throws Exception {
        Map<String, Object> map = LlmJson.parseMap(
                "{\"problemStatement\":\"SSRF\",\"techNeeded\":[\"npm\"]}");

        assertThat(map.get("problemStatement")).isEqualTo("SSRF");
        assertThat(map.get("techNeeded")).isEqualTo(List.of("npm"));
    }

    @Test
    void parseOrRaw_returnsParsedObjectForFencedJson() {
        Object parsed = LlmJson.parseOrRaw("""
                ```json
                {"gapFlag":true}
                ```
                """);

        assertThat(parsed).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) parsed).get("gapFlag")).isEqualTo(true);
    }

    @Test
    void parseOrRaw_returnsStrippedStringWhenNotJson() {
        Object result = LlmJson.parseOrRaw("```\nnot-json\n```");
        assertThat(result).isEqualTo("not-json");
    }

    @Test
    void parseOrRaw_extractsJsonObjectFromProseAndFences() {
        Object parsed = LlmJson.parseOrRaw("""
                Knowledge Card Synth here!

                Here's the assembled Knowledge Card:

                ```json
                {
                  "gapFlag": true,
                  "query": "How did we migrate from NewRelic to OpenTelemetry?"
                }
                ```

                Note: used architecture.md schema.
                """);

        assertThat(parsed).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) parsed;
        assertThat(map.get("gapFlag")).isEqualTo(true);
        assertThat(map.get("query")).isEqualTo("How did we migrate from NewRelic to OpenTelemetry?");
    }
}
