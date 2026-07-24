package com.company.sage.clients.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.company.sage.config.GraphRagProperties;
import com.company.sage.model.GraphRagHealthResponse;
import com.company.sage.model.GraphRetrieveRequest;
import com.company.sage.model.RetrieveResponse;
import com.company.sage.model.SemanticRetrieveRequest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class GraphRagClientTest {

    private GraphRagClient graphRagClient;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        GraphRagProperties properties = new GraphRagProperties();
        properties.setBaseUrl("http://localhost:8000");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        graphRagClient = new GraphRagClient(properties, builder);
    }

    @Test
    void testRetrieveSemanticSuccess() {
        String jsonResponse = """
                {
                  "hits": [{
                    "doc_id": "178025",
                    "source": "orion_metadata",
                    "passage": "Implemented a server-side proxy..."
                  }],
                  "query_time_ms": 210,
                  "total_found": 1
                }
                """;

        server.expect(requestTo("http://localhost:8000/retrieve/semantic"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Correlation-ID", "test-correlation-id"))
                .andExpect(header("X-Correlation-Id", "test-correlation-id"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        SemanticRetrieveRequest request = new SemanticRetrieveRequest("SSRF mitigation", 5, 0.60);
        RetrieveResponse response = graphRagClient.retrieveSemantic(request, "test-correlation-id");

        assertThat(response).isNotNull();
        assertThat(response.getQueryTimeMs()).isEqualTo(210);
        assertThat(response.getTotalFound()).isEqualTo(1);
        assertThat(response.getHits()).hasSize(1);
        assertThat(response.getHits().get(0).getDocId()).isEqualTo("178025");
        server.verify();
    }

    @Test
    void testRetrieveSemanticServerErrorFailSoft() {
        server.expect(requestTo("http://localhost:8000/retrieve/semantic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        SemanticRetrieveRequest request = new SemanticRetrieveRequest("SSRF mitigation", 5, 0.60);
        RetrieveResponse response = graphRagClient.retrieveSemantic(request, "test-correlation-id");

        // Fail-soft: should return empty list of hits and 0 query time/total found
        assertThat(response).isNotNull();
        assertThat(response.getHits()).isEmpty();
        assertThat(response.getTotalFound()).isEqualTo(0);
        server.verify();
    }

    @Test
    void testRetrieveSemanticDocumentLinkArraySuccess() {
        // Python may return documentLink as string[]; Java keeps a single String (first element).
        String jsonResponse = """
                {
                  "hits": [{
                    "doc_id": "178025",
                    "source": "orion_metadata",
                    "passage": "Implemented a server-side proxy...",
                    "metadata": {
                      "title": "SSRF-safe external image loader",
                      "documentLink": ["https://example.com/a.pdf"]
                    }
                  }],
                  "query_time_ms": 210,
                  "total_found": 1
                }
                """;

        server.expect(requestTo("http://localhost:8000/retrieve/semantic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        SemanticRetrieveRequest request = new SemanticRetrieveRequest("SSRF mitigation", 5, 0.60);
        RetrieveResponse response = graphRagClient.retrieveSemantic(request, "test-correlation-id");

        assertThat(response).isNotNull();
        assertThat(response.getHits()).hasSize(1);
        assertThat(response.getHits().get(0).getDocId()).isEqualTo("178025");
        assertThat(response.getHits().get(0).getMetadata().getDocumentLink())
                .isEqualTo("https://example.com/a.pdf");
        assertThat(response.getTotalFound()).isEqualTo(1);
        server.verify();
    }

    @Test
    void testRetrieveGraphSuccess() {
        String jsonResponse = """
                {
                  "hits": [{
                    "doc_id": "178025",
                    "source": "orion_metadata",
                    "passage": "Implemented a server-side proxy..."
                  }],
                  "query_time_ms": 180,
                  "total_found": 1
                }
                """;

        server.expect(requestTo("http://localhost:8000/retrieve/graph"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Correlation-ID", "test-correlation-id"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GraphRetrieveRequest request = new GraphRetrieveRequest(List.of("SSRF"), 5);
        RetrieveResponse response = graphRagClient.retrieveGraph(request, "test-correlation-id");

        assertThat(response).isNotNull();
        assertThat(response.getQueryTimeMs()).isEqualTo(180);
        assertThat(response.getTotalFound()).isEqualTo(1);
        assertThat(response.getHits()).hasSize(1);
        assertThat(response.getHits().get(0).getDocId()).isEqualTo("178025");
        server.verify();
    }

    @Test
    void testRetrieveSemanticAndGraphWithRecordedFixtures() throws Exception {
        assumeTrue(
                resourceExists("/fixtures/graphrag/happy-path-queries.json"),
                "No local Graph RAG fixtures; skip recorded client test");
        String fixtureDir = firstHappyPathFixtureDir();
        assumeTrue(
                resourceExists(fixtureDir + "semantic.response.json")
                        && resourceExists(fixtureDir + "graph.response.json"),
                "No local recorded fixtures; skip recorded client test");

        String semanticJson = readClasspath(fixtureDir + "semantic.response.json");
        String graphJson = readClasspath(fixtureDir + "graph.response.json");

        server.expect(requestTo("http://localhost:8000/retrieve/semantic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(semanticJson, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:8000/retrieve/graph"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(graphJson, MediaType.APPLICATION_JSON));

        RetrieveResponse semantic = graphRagClient.retrieveSemantic(
                new SemanticRetrieveRequest("Adcolony event processing", 5, 0.60), "fixture-corr");
        RetrieveResponse graph = graphRagClient.retrieveGraph(
                new GraphRetrieveRequest(List.of("Kafka", "EC2"), 5), "fixture-corr");

        assertThat(semantic.getHits()).isNotEmpty();
        assertThat(graph.getHits()).isNotEmpty();
        assertThat(semantic.getHits().get(0).getDocId()).isNotBlank();
        assertThat(graph.getHits().get(0).getDocId()).isNotBlank();
        server.verify();
    }

    private static String firstHappyPathFixtureDir() throws Exception {
        try (InputStream in = GraphRagClientTest.class.getResourceAsStream(
                "/fixtures/graphrag/happy-path-queries.json")) {
            assertThat(in).isNotNull();
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(in);
            String fixtureDir = root.path("queries").path(0).path("fixtureDir").asText(null);
            assertThat(fixtureDir).isNotBlank();
            return "/fixtures/graphrag/" + fixtureDir + "/";
        }
    }

    private static boolean resourceExists(String classpath) {
        try (InputStream in = GraphRagClientTest.class.getResourceAsStream(classpath)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readClasspath(String path) throws Exception {
        try (InputStream in = GraphRagClientTest.class.getResourceAsStream(path)) {
            assertThat(in).as("classpath resource %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void testRetrieveGraphServerErrorFailSoft() {
        server.expect(requestTo("http://localhost:8000/retrieve/graph"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        GraphRetrieveRequest request = new GraphRetrieveRequest(List.of("SSRF"), 5);
        RetrieveResponse response = graphRagClient.retrieveGraph(request, "test-correlation-id");

        // Fail-soft: should return empty list of hits
        assertThat(response).isNotNull();
        assertThat(response.getHits()).isEmpty();
        assertThat(response.getTotalFound()).isEqualTo(0);
        server.verify();
    }

    @Test
    void testGetHealthSuccess() {
        String jsonResponse = """
                {
                  "status": "ok",
                  "last_seed_run": "2025-06-12",
                  "indexed_records": 1204,
                  "neo4j_reachable": true
                }
                """;

        server.expect(requestTo("http://localhost:8000/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GraphRagHealthResponse response = graphRagClient.getHealth();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("ok");
        assertThat(response.getLastSeedRun()).isEqualTo("2025-06-12");
        assertThat(response.getIndexedRecords()).isEqualTo(1204);
        assertThat(response.isNeo4jReachable()).isTrue();
        server.verify();
    }

    @Test
    void testGetHealthFailSoft() {
        server.expect(requestTo("http://localhost:8000/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        GraphRagHealthResponse response = graphRagClient.getHealth();

        // Fail-soft: unreachable should map to status degraded and neo4jReachable false
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("degraded");
        assertThat(response.isNeo4jReachable()).isFalse();
        server.verify();
    }
}
