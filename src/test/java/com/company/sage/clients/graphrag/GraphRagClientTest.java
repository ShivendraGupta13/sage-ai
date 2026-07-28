package com.company.sage.clients.graphrag;

import com.company.sage.config.SageProperties;
import com.company.sage.model.GraphRetrieveRequest;
import com.company.sage.model.RetrieveResponse;
import com.company.sage.model.SemanticRetrieveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GraphRagClientTest {

    private GraphRagClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        SageProperties properties = new SageProperties(
            new SageProperties.GraphRag("http://localhost:8000"),
            new SageProperties.Adk(new SageProperties.Adk.Llm("http://localhost:11434", "llama3.2", 0.0)),
            new SageProperties.Retrieval(5, 0.60),
            new SageProperties.Scoring(0.6, 0.4, 0.1, 0.60)
        );

        client = new GraphRagClient(builder, properties);
    }

    @Test
    void shouldRetrieveSemanticSuccessfullyWithUseLlmFalseAndCorrelationIdHeader() {
        String mockResponseJson = """
            {
              "hits": [{
                "doc_id": "178025",
                "source": "orion_metadata",
                "vectorScore": 0.83,
                "passage": "Implemented a server-side proxy...",
                "metadata": {
                  "title": "SSRF-safe external image loader",
                  "teamName": "Payments Platform",
                  "teamId": "42",
                  "people": [{ "personId": "65", "name": "Priya Sharma" }],
                  "technologies": ["SSRF mitigation"],
                  "documentLink": "https://example.com",
                  "category": "HARD_PROBLEMS",
                  "sourceAttribution": "Orion API"
                }
              }],
              "query_time_ms": 120,
              "total_found": 1
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/retrieve/semantic"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
            .andExpect(header("X-Correlation-Id", "test-corr-123"))
            .andExpect(jsonPath("$.problemStatement").value("SSRF issue"))
            .andExpect(jsonPath("$.use_llm").value(false))
            .andExpect(jsonPath("$.top_k").value(5))
            .andExpect(jsonPath("$.min_score").value(0.60))
            .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        SemanticRetrieveRequest req = new SemanticRetrieveRequest("SSRF issue", 5, 0.60, false);
        RetrieveResponse response = client.retrieveSemantic(req, "test-corr-123");

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.hits()).hasSize(1);
        assertThat(response.hits().getFirst().docId()).isEqualTo("178025");
        assertThat(response.hits().getFirst().vectorScore()).isEqualTo(0.83);
    }

    @Test
    void shouldFailSoftToEmptyHitsOnSemantic5xxError() {
        mockServer.expect(requestTo("http://localhost:8000/retrieve/semantic"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        SemanticRetrieveRequest req = new SemanticRetrieveRequest("SSRF issue", 5, 0.60, false);
        RetrieveResponse response = client.retrieveSemantic(req, "test-corr-500");

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.hits()).isEmpty();
    }

    @Test
    void shouldRetrieveGraphSuccessfullyWithCorrelationIdHeader() {
        String mockResponseJson = """
            {
              "hits": [{
                "doc_id": "178025",
                "source": "orion_metadata",
                "graphScore": 1.0,
                "matchedTags": ["SSRF mitigation"],
                "pathDescription": "Tech -> HP",
                "passage": "Implemented a server-side proxy...",
                "metadata": {
                  "title": "SSRF-safe external image loader",
                  "teamName": "Payments Platform",
                  "teamId": "42",
                  "people": [],
                  "technologies": ["SSRF mitigation"],
                  "documentLink": null,
                  "category": "HARD_PROBLEMS",
                  "sourceAttribution": "Orion API"
                }
              }],
              "query_time_ms": 100,
              "total_found": 1
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/retrieve/graph"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
            .andExpect(header("X-Correlation-Id", "test-corr-456"))
            .andExpect(jsonPath("$.techNeeded[0]").value("SSRF mitigation"))
            .andExpect(jsonPath("$.top_k").value(5))
            .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        GraphRetrieveRequest req = new GraphRetrieveRequest(List.of("SSRF mitigation"), 5);
        RetrieveResponse response = client.retrieveGraph(req, "test-corr-456");

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.hits()).hasSize(1);
        assertThat(response.hits().getFirst().graphScore()).isEqualTo(1.0);
    }

    @Test
    void shouldFailSoftToEmptyHitsOnGraph5xxError() {
        mockServer.expect(requestTo("http://localhost:8000/retrieve/graph"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        GraphRetrieveRequest req = new GraphRetrieveRequest(List.of("SSRF mitigation"), 5);
        RetrieveResponse response = client.retrieveGraph(req, "test-corr-503");

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.hits()).isEmpty();
    }

    @Test
    void shouldReturnTrueWhenGraphRagHealthIsReachableAndOk() {
        String healthJson = """
            {
              "status": "ok",
              "neo4j_reachable": true
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/health"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(healthJson, MediaType.APPLICATION_JSON));

        boolean reachable = client.checkHealth();

        mockServer.verify();
        assertThat(reachable).isTrue();
    }

    @Test
    void shouldReturnFalseWhenGraphRagHealthIsUnreachable() {
        mockServer.expect(requestTo("http://localhost:8000/health"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        boolean reachable = client.checkHealth();

        mockServer.verify();
        assertThat(reachable).isFalse();
    }
}
