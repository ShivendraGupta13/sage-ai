package com.company.sage.clients.graphrag;

import com.company.sage.config.GraphRagProperties;
import com.company.sage.model.GraphRagHealthResponse;
import com.company.sage.model.GraphRetrieveRequest;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
import com.company.sage.model.SemanticRetrieveRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
    private static final int MAX_LOGGED_BODY_CHARS = 4000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        log.info(
                "Graph RAG request correlationId={} endpoint=/retrieve/semantic problemStatement={} topK={} minScore={} useLlm={}",
                corrId,
                request != null ? request.getProblemStatement() : null,
                request != null ? request.getTopK() : null,
                request != null ? request.getMinScore() : null,
                request != null && request.isUseLlm());
        return postRetrieve("/retrieve/semantic", request, corrId);
    }

    /**
     * Executes a graph traversal search query against downstream Graph RAG service.
     */
    public RetrieveResponse retrieveGraph(GraphRetrieveRequest request, String correlationId) {
        String corrId = correlationId != null ? correlationId : "unknown";
        log.info(
                "Graph RAG request correlationId={} endpoint=/retrieve/graph techNeeded={} topK={}",
                corrId,
                request != null ? request.getTechNeeded() : null,
                request != null ? request.getTopK() : null);
        return postRetrieve("/retrieve/graph", request, corrId);
    }

    /**
     * Checks the health of the downstream Graph RAG service.
     */
    public GraphRagHealthResponse getHealth() {
        log.info("Graph RAG request endpoint=/health");
        try {
            return restClient.get()
                    .uri("/health")
                    .exchange((request, response) -> {
                        String body = readBody(response.getBody());
                        int status = response.getStatusCode().value();
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.warn(
                                    "Downstream Graph RAG health check failed status={} body={}",
                                    status,
                                    truncateForLog(body));
                            return new GraphRagHealthResponse("degraded", null, 0, false);
                        }
                        try {
                            GraphRagHealthResponse parsed = MAPPER.readValue(body, GraphRagHealthResponse.class);
                            GraphRagHealthResponse result = parsed != null
                                    ? parsed
                                    : new GraphRagHealthResponse("degraded", null, 0, false);
                            log.info(
                                    "Graph RAG response endpoint=/health status={} neo4jReachable={}",
                                    result.getStatus(),
                                    result.isNeo4jReachable());
                            return result;
                        } catch (IOException e) {
                            log.warn(
                                    "Downstream Graph RAG health deserialize failed status={} body={} cause={}",
                                    status,
                                    truncateForLog(body),
                                    e.getMessage());
                            return new GraphRagHealthResponse("degraded", null, 0, false);
                        }
                    });
        } catch (RestClientException e) {
            log.warn("Downstream Graph RAG health check failed (unreachable/degraded): {}", e.getMessage());
            return new GraphRagHealthResponse("degraded", null, 0, false);
        }
    }

    private RetrieveResponse postRetrieve(String endpoint, Object requestBody, String corrId) {
        try {
            return restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CORRELATION_HEADER_1, corrId)
                    .header(CORRELATION_HEADER_2, corrId)
                    .body(requestBody)
                    .exchange((request, response) -> {
                        String body = readBody(response.getBody());
                        int status = response.getStatusCode().value();
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.error(
                                    "Fail-soft: calling {} failed correlationId={} httpStatus={} body={}",
                                    endpoint,
                                    corrId,
                                    status,
                                    truncateForLog(body));
                            return emptyRetrieveResponse();
                        }
                        try {
                            RetrieveResponse parsed = MAPPER.readValue(body, RetrieveResponse.class);
                            RetrieveResponse result = parsed != null ? parsed : emptyRetrieveResponse();
                            logRetrieveSuccess(corrId, endpoint, result);
                            return result;
                        } catch (IOException e) {
                            log.error(
                                    "Fail-soft: calling {} deserialize failed (Java DTO vs Python JSON mismatch) "
                                            + "correlationId={} httpStatus={} cause={} body={}",
                                    endpoint,
                                    corrId,
                                    status,
                                    e.getMessage(),
                                    truncateForLog(body));
                            return emptyRetrieveResponse();
                        }
                    });
        } catch (RestClientException e) {
            log.error(
                    "Fail-soft: calling {} failed with correlationId={}: {}",
                    endpoint,
                    corrId,
                    e.getMessage());
            return emptyRetrieveResponse();
        }
    }

    private static RetrieveResponse emptyRetrieveResponse() {
        return new RetrieveResponse(new ArrayList<>(), 0, 0);
    }

    private static String readBody(InputStream bodyStream) throws IOException {
        if (bodyStream == null) {
            return "";
        }
        return new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String truncateForLog(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        if (body.length() <= MAX_LOGGED_BODY_CHARS) {
            return body;
        }
        return body.substring(0, MAX_LOGGED_BODY_CHARS) + "...(truncated, totalChars=" + body.length() + ")";
    }

    private void logRetrieveSuccess(String correlationId, String endpoint, RetrieveResponse response) {
        List<String> docIds = response.getHits() == null
                ? List.of()
                : response.getHits().stream()
                        .map(RetrieveHit::getDocId)
                        .collect(Collectors.toList());
        int hitCount = response.getHits() == null ? 0 : response.getHits().size();
        log.info(
                "Graph RAG response correlationId={} endpoint={} hitCount={} totalFound={} queryTimeMs={} docIds={}",
                correlationId,
                endpoint,
                hitCount,
                response.getTotalFound(),
                response.getQueryTimeMs(),
                docIds);
    }
}
