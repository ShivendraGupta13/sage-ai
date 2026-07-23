package com.company.sage.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DtoFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSemanticRetrieveResponseDeserialization() throws Exception {
        String json = """
                {
                  "hits": [{
                    "doc_id": "178025",
                    "source": "orion_metadata",
                    "vectorScore": 0.83,
                    "passage": "Implemented a server-side proxy using got-scrubbing library…",
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

        RetrieveResponse response = objectMapper.readValue(json, RetrieveResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getQueryTimeMs()).isEqualTo(210);
        assertThat(response.getTotalFound()).isEqualTo(1);
        assertThat(response.getHits()).hasSize(1);

        RetrieveHit hit = response.getHits().get(0);
        assertThat(hit.getDocId()).isEqualTo("178025");
        assertThat(hit.getSource()).isEqualTo("orion_metadata");
        assertThat(hit.getVectorScore()).isEqualTo(0.83);
        assertThat(hit.getGraphScore()).isNull();
        assertThat(hit.getPassage()).contains("got-scrubbing");

        RetrieveHitMetadata meta = hit.getMetadata();
        assertThat(meta).isNotNull();
        assertThat(meta.getTitle()).isEqualTo("SSRF-safe external image loader");
        assertThat(meta.getTeamName()).isEqualTo("Payments Platform");
        assertThat(meta.getTeamId()).isEqualTo("42");
        assertThat(meta.getCategory()).isEqualTo("HARD_PROBLEMS");
        assertThat(meta.getSourceAttribution()).isEqualTo("Orion API");
        assertThat(meta.getDocumentLink()).isEqualTo("https://talenticacontact.freshdesk.com/a/tickets/1234");
        assertThat(meta.getTechnologies()).containsExactly("SSRF mitigation", "npm", "Node.js");
        assertThat(meta.getPeople()).hasSize(1);
        
        RetrievePerson person = meta.getPeople().get(0);
        assertThat(person.getPersonId()).isEqualTo("65");
        assertThat(person.getName()).isEqualTo("Priya Sharma");
    }

    @Test
    void testGraphRetrieveResponseDeserialization() throws Exception {
        String json = """
                {
                  "hits": [{
                    "doc_id": "178025",
                    "source": "orion_metadata",
                    "graphScore": 1.0,
                    "matchedTags": ["SSRF mitigation"],
                    "pathDescription": "Technology[SSRF mitigation] -> HardProblem[SSRF-safe image loader] -> Team[Payments Platform]",
                    "passage": "Implemented a server-side proxy using got-scrubbing library…",
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
                  "query_time_ms": 180,
                  "total_found": 1
                }
                """;

        RetrieveResponse response = objectMapper.readValue(json, RetrieveResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getQueryTimeMs()).isEqualTo(180);
        assertThat(response.getTotalFound()).isEqualTo(1);
        assertThat(response.getHits()).hasSize(1);

        RetrieveHit hit = response.getHits().get(0);
        assertThat(hit.getDocId()).isEqualTo("178025");
        assertThat(hit.getSource()).isEqualTo("orion_metadata");
        assertThat(hit.getGraphScore()).isEqualTo(1.0);
        assertThat(hit.getVectorScore()).isNull();
        assertThat(hit.getMatchedTags()).containsExactly("SSRF mitigation");
        assertThat(hit.getPathDescription()).contains("HardProblem[SSRF-safe image loader]");
    }

    @Test
    void testSemanticRetrieveRequestSerialization() throws Exception {
        SemanticRetrieveRequest request = new SemanticRetrieveRequest(
                "SSRF exposure prevention", 10, 0.75);

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"problemStatement\":\"SSRF exposure prevention\"");
        assertThat(json).contains("\"top_k\":10");
        assertThat(json).contains("\"min_score\":0.75");
    }

    @Test
    void testGraphRetrieveRequestSerialization() throws Exception {
        GraphRetrieveRequest request = new GraphRetrieveRequest(
                List.of("SSRF mitigation", "npm"), 7);

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"techNeeded\":[\"SSRF mitigation\",\"npm\"]");
        assertThat(json).contains("\"top_k\":7");
    }

    @Test
    void testErrorResponseSerialization() throws Exception {
        ValidationError valError = new ValidationError("top_k", "must be between 1 and 20", "50");
        ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Unprocessable Entity",
                422,
                "TOP_K_OUT_OF_RANGE",
                "Some search options are invalid.",
                "top_k must be between 1 and 20",
                "/retrieve/semantic",
                "c0ffee-123",
                List.of(valError)
        );

        String json = objectMapper.writeValueAsString(error);

        assertThat(json).contains("\"type\":\"about:blank\"");
        assertThat(json).contains("\"title\":\"Unprocessable Entity\"");
        assertThat(json).contains("\"status\":422");
        assertThat(json).contains("\"code\":\"TOP_K_OUT_OF_RANGE\"");
        assertThat(json).contains("\"correlationId\":\"c0ffee-123\"");
        assertThat(json).contains("\"errors\":[{\"field\":\"top_k\",\"reason\":\"must be between 1 and 20\",\"rejectedValue\":\"50\"}]");
    }
}
