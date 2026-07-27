package com.company.sage.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DtoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldDeserializeSemanticRetrieveResponseFixture() throws Exception {
        String json = """
            {
              "hits": [{
                "doc_id": "178025",
                "source": "orion_metadata",
                "vectorScore": 0.83,
                "passage": "Implemented a server-side proxy using got-scrubbing library...",
                "metadata": {
                  "title": "SSRF-safe external image loader",
                  "teamName": "Payments Platform",
                  "teamId": "42",
                  "people": [{ "personId": "65", "name": "Priya Sharma" }],
                  "technologies": ["SSRF mitigation", "npm", "Node.js"],
                  "documentLink": "https://talenticacontact.freshdesk.com/a/tickets/1234",
                  "category": "HARD_PROBLEMS",
                  "sourceAttribution": "Orion API"
                }
              }],
              "query_time_ms": 210,
              "total_found": 1
            }
            """;

        RetrieveResponse response = mapper.readValue(json, RetrieveResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.queryTimeMs()).isEqualTo(210L);
        assertThat(response.totalFound()).isEqualTo(1);
        assertThat(response.hits()).hasSize(1);

        RetrieveHit hit = response.hits().getFirst();
        assertThat(hit.docId()).isEqualTo("178025");
        assertThat(hit.source()).isEqualTo("orion_metadata");
        assertThat(hit.vectorScore()).isEqualTo(0.83);
        assertThat(hit.passage()).startsWith("Implemented a server-side proxy");

        RetrieveMetadata metadata = hit.metadata();
        assertThat(metadata.title()).isEqualTo("SSRF-safe external image loader");
        assertThat(metadata.teamName()).isEqualTo("Payments Platform");
        assertThat(metadata.teamId()).isEqualTo("42");
        assertThat(metadata.people()).hasSize(1);
        assertThat(metadata.people().getFirst().personId()).isEqualTo("65");
        assertThat(metadata.people().getFirst().name()).isEqualTo("Priya Sharma");
        assertThat(metadata.technologies()).containsExactly("SSRF mitigation", "npm", "Node.js");
        assertThat(metadata.getFirstDocumentLink()).isEqualTo("https://talenticacontact.freshdesk.com/a/tickets/1234");
        assertThat(metadata.category()).isEqualTo("HARD_PROBLEMS");
        assertThat(metadata.sourceAttribution()).isEqualTo("Orion API");
    }

    @Test
    void shouldDeserializeDocumentLinkAsJsonArray() throws Exception {
        String json = """
            {
              "title": "Title",
              "documentLink": ["https://example.com/doc.pdf"]
            }
            """;
        RetrieveMetadata metadata = mapper.readValue(json, RetrieveMetadata.class);
        assertThat(metadata.documentLink()).containsExactly("https://example.com/doc.pdf");
        assertThat(metadata.getFirstDocumentLink()).isEqualTo("https://example.com/doc.pdf");
    }

    @Test
    void shouldDeserializeGraphRetrieveResponseFixture() throws Exception {
        String json = """
            {
              "hits": [{
                "doc_id": "178025",
                "source": "orion_metadata",
                "graphScore": 1.0,
                "matchedTags": ["SSRF mitigation"],
                "pathDescription": "Technology[SSRF mitigation] -> HardProblem[SSRF-safe image loader]",
                "passage": "Implemented a server-side proxy...",
                "metadata": {
                  "title": "SSRF-safe external image loader",
                  "teamName": "Payments Platform",
                  "teamId": "42",
                  "people": [{ "personId": "65", "name": "Priya Sharma" }],
                  "technologies": ["SSRF mitigation"],
                  "documentLink": null,
                  "category": "HARD_PROBLEMS",
                  "sourceAttribution": "Orion API"
                }
              }],
              "query_time_ms": 180,
              "total_found": 1
            }
            """;

        RetrieveResponse response = mapper.readValue(json, RetrieveResponse.class);

        assertThat(response).isNotNull();
        RetrieveHit hit = response.hits().getFirst();
        assertThat(hit.docId()).isEqualTo("178025");
        assertThat(hit.graphScore()).isEqualTo(1.0);
        assertThat(hit.matchedTags()).containsExactly("SSRF mitigation");
        assertThat(hit.pathDescription()).contains("Technology[SSRF mitigation]");
    }

    @Test
    void shouldSerializeSemanticRetrieveRequestWithSnakeCaseKnobs() throws Exception {
        SemanticRetrieveRequest req = new SemanticRetrieveRequest("SSRF issue", 5, 0.60, false);
        String json = mapper.writeValueAsString(req);

        assertThat(json).contains("\"top_k\":5");
        assertThat(json).contains("\"min_score\":0.6");
        assertThat(json).contains("\"use_llm\":false");
        assertThat(json).contains("\"problemStatement\":\"SSRF issue\"");
    }

    @Test
    void shouldSerializeGraphRetrieveRequestWithSnakeCaseKnobs() throws Exception {
        GraphRetrieveRequest req = new GraphRetrieveRequest(List.of("SSRF mitigation"), 5);
        String json = mapper.writeValueAsString(req);

        assertThat(json).contains("\"top_k\":5");
        assertThat(json).contains("\"techNeeded\":[\"SSRF mitigation\"]");
    }

    @Test
    void shouldSerializeKnowledgeCardCorrectly() throws Exception {
        CardResult result = new CardResult(
            1, 0.87, List.of("semantic", "graph"),
            "Payments Platform", "SSRF-safe external image loader",
            "HARD_PROBLEMS", List.of("Priya Sharma"),
            "Implemented proxy", "https://example.com",
            "Semantic similarity 0.83; graph tag match", List.of("Orion API")
        );

        KnowledgeCard card = new KnowledgeCard(
            "How did we handle SSRF?",
            "Safely fetching images without SSRF",
            List.of("SSRF mitigation"),
            "Yes — 1 team solved this",
            List.of(result),
            false,
            null
        );

        String json = mapper.writeValueAsString(card);
        assertThat(json).contains("\"query\":\"How did we handle SSRF?\"");
        assertThat(json).contains("\"gapFlag\":false");
        assertThat(json).contains("\"confidenceScore\":0.87");
    }

    @Test
    void shouldSerializeSharedErrorSchemaCorrectly() throws Exception {
        ApiErrorResponse error = new ApiErrorResponse(
            "about:blank", "Bad Request", 400, "QUERY_BLANK",
            "Query cannot be blank", "The query field was empty",
            "/ask", "corr-123", List.of(new FieldErrorDetail("query", "must not be blank", ""))
        );

        String json = mapper.writeValueAsString(error);
        assertThat(json).contains("\"code\":\"QUERY_BLANK\"");
        assertThat(json).contains("\"correlationId\":\"corr-123\"");
        assertThat(json).contains("\"errors\":[");
    }
}
