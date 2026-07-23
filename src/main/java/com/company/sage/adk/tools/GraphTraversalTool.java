package com.company.sage.adk.tools;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.RetrievalProperties;
import com.company.sage.model.GraphRetrieveRequest;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ADK Tool wrapper for downstream graph traversal retrieval.
 */
@Component
public class GraphTraversalTool {

    private static final Logger log = LoggerFactory.getLogger(GraphTraversalTool.class);

    private final GraphRagClient graphRagClient;
    private final RetrievalProperties retrievalProperties;

    public GraphTraversalTool(GraphRagClient graphRagClient, RetrievalProperties retrievalProperties) {
        this.graphRagClient = graphRagClient;
        this.retrievalProperties = retrievalProperties;
    }

    /**
     * Executes graph traversal search using the technology tags.
     */
    public List<RetrieveHit> graphTraversal(
            @Schema(name = "techNeeded", description = "The list of tech stack tags or patterns to traverse", optional = false)
            List<String> techNeeded,
            @Schema(name = "topK", description = "The maximum number of matches to retrieve", optional = true)
            Integer topK,
            ToolContext toolContext) {

        String correlationId = toolContext != null ? toolContext.sessionId() : "unknown";
        int finalTopK = topK != null ? topK : retrievalProperties.getTopK();

        try {
            GraphRetrieveRequest req = new GraphRetrieveRequest(techNeeded, finalTopK);
            RetrieveResponse response = graphRagClient.retrieveGraph(req, correlationId);
            return response != null ? response.getHits() : new ArrayList<>();
        } catch (Exception e) {
            log.error("Fail-soft in GraphTraversalTool: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
