package com.company.sage.clients.graphrag;

import com.company.sage.config.GraphRagProperties;
import com.company.sage.model.GraphRagHealthResponse;
import com.company.sage.model.GraphRetrieveRequest;
import com.company.sage.model.RetrieveResponse;
import com.company.sage.model.SemanticRetrieveRequest;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Rest HTTP client to communicate with the downstream Graph RAG Python service.
 */
@Component
public class GraphRagClient {

    private static final Logger log = LoggerFactory.getLogger(GraphRagClient.class);
    private static final String CORRELATION_HEADER_1 = "X-Correlation-ID";
    private static final String CORRELATION_HEADER_2 = "X-Correlation-Id";

    private final RestClient restClient;

    public GraphRagClient(GraphRagProperties properties, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    /**
     * Executes a semantic search query against downstream Graph RAG service.
     */
    public RetrieveResponse retrieveSemantic(SemanticRetrieveRequest request, String correlationId) {
        String corrId = correlationId != null ? correlationId : "unknown";
        try {
            RetrieveResponse response = restClient.post()
                    .uri("/retrieve/semantic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CORRELATION_HEADER_1, corrId)
                    .header(CORRELATION_HEADER_2, corrId)
                    .body(request)
                    .retrieve()
                    .body(RetrieveResponse.class);
            return response != null ? response : new RetrieveResponse(new ArrayList<>(), 0, 0);
        } catch (RestClientException e) {
            log.error("Fail-soft: calling /retrieve/semantic failed with correlationId={}: {}", corrId, e.getMessage());
            return new RetrieveResponse(new ArrayList<>(), 0, 0);
        }
    }

    /**
     * Executes a graph traversal search query against downstream Graph RAG service.
     */
    public RetrieveResponse retrieveGraph(GraphRetrieveRequest request, String correlationId) {
        String corrId = correlationId != null ? correlationId : "unknown";
        try {
            RetrieveResponse response = restClient.post()
                    .uri("/retrieve/graph")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CORRELATION_HEADER_1, corrId)
                    .header(CORRELATION_HEADER_2, corrId)
                    .body(request)
                    .retrieve()
                    .body(RetrieveResponse.class);
            return response != null ? response : new RetrieveResponse(new ArrayList<>(), 0, 0);
        } catch (RestClientException e) {
            log.error("Fail-soft: calling /retrieve/graph failed with correlationId={}: {}", corrId, e.getMessage());
            return new RetrieveResponse(new ArrayList<>(), 0, 0);
        }
    }

    /**
     * Checks the health of the downstream Graph RAG service.
     */
    public GraphRagHealthResponse getHealth() {
        try {
            GraphRagHealthResponse response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(GraphRagHealthResponse.class);
            return response != null ? response : new GraphRagHealthResponse("degraded", null, 0, false);
        } catch (RestClientException e) {
            log.warn("Downstream Graph RAG health check failed (unreachable/degraded): {}", e.getMessage());
            return new GraphRagHealthResponse("degraded", null, 0, false);
        }
    }
}
