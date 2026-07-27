package com.company.sage.clients.graphrag;

import com.company.sage.config.SageProperties;
import com.company.sage.model.GraphRetrieveRequest;
import com.company.sage.model.RetrieveResponse;
import com.company.sage.model.SemanticRetrieveRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class GraphRagClient {

    private static final Logger log = LoggerFactory.getLogger(GraphRagClient.class);
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final RestClient restClient;

    public GraphRagClient(RestClient.Builder restClientBuilder, SageProperties properties) {
        String baseUrl = properties.graphRag().baseUrl();
        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .build();
    }

    public RetrieveResponse retrieveSemantic(SemanticRetrieveRequest request, String correlationId) {
        // Enforce use_llm = false per retrieve API contract SoT
        SemanticRetrieveRequest enforcedRequest = new SemanticRetrieveRequest(
            request.problemStatement(),
            request.topK(),
            request.minScore(),
            false
        );

        try {
            RetrieveResponse response = restClient.post()
                .uri("/retrieve/semantic")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (correlationId != null && !correlationId.isBlank()) {
                        headers.set(CORRELATION_HEADER, correlationId);
                    }
                })
                .body(enforcedRequest)
                .retrieve()
                .body(RetrieveResponse.class);

            // Post-review enhancement: Null-safety check to prevent null return if body deserialization returns null
            return (response != null) ? response : new RetrieveResponse(List.of(), 0L, 0);
        } catch (Exception e) {
            log.warn("Fail-soft: POST /retrieve/semantic failed for correlationId {}: {}", correlationId, e.getMessage());
            return new RetrieveResponse(List.of(), 0L, 0);
        }
    }

    public RetrieveResponse retrieveGraph(GraphRetrieveRequest request, String correlationId) {
        try {
            RetrieveResponse response = restClient.post()
                .uri("/retrieve/graph")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (correlationId != null && !correlationId.isBlank()) {
                        headers.set(CORRELATION_HEADER, correlationId);
                    }
                })
                .body(request)
                .retrieve()
                .body(RetrieveResponse.class);

            // Post-review enhancement: Null-safety check to prevent null return if body deserialization returns null
            return (response != null) ? response : new RetrieveResponse(List.of(), 0L, 0);
        } catch (Exception e) {
            log.warn("Fail-soft: POST /retrieve/graph failed for correlationId {}: {}", correlationId, e.getMessage());
            return new RetrieveResponse(List.of(), 0L, 0);
        }
    }

    public boolean checkHealth() {
        try {
            var response = restClient.get()
                .uri("/health")
                .retrieve()
                .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Graph RAG health probe failed: {}", e.getMessage());
            return false;
        }
    }
}
