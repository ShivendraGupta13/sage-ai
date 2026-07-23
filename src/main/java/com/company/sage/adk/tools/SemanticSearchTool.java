package com.company.sage.adk.tools;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.RetrievalProperties;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
import com.company.sage.model.SemanticRetrieveRequest;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ADK Tool wrapper for downstream semantic search retrieval.
 */
@Component
public class SemanticSearchTool {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchTool.class);

    private final GraphRagClient graphRagClient;
    private final RetrievalProperties retrievalProperties;

    public SemanticSearchTool(GraphRagClient graphRagClient, RetrievalProperties retrievalProperties) {
        this.graphRagClient = graphRagClient;
        this.retrievalProperties = retrievalProperties;
    }

    /**
     * Executes semantic search using the given problem statement.
     */
    public List<RetrieveHit> semanticSearch(
            @Schema(name = "problemStatement", description = "The extracted problem statement to search prior art for", optional = false)
            String problemStatement,
            @Schema(name = "topK", description = "The maximum number of matches to retrieve", optional = true)
            Integer topK,
            ToolContext toolContext) {

        String correlationId = toolContext != null ? toolContext.sessionId() : "unknown";
        int finalTopK = topK != null ? topK : retrievalProperties.getTopK();

        try {
            SemanticRetrieveRequest req = new SemanticRetrieveRequest(
                    problemStatement, finalTopK, retrievalProperties.getMinScore());
            RetrieveResponse response = graphRagClient.retrieveSemantic(req, correlationId);
            return response != null ? response.getHits() : new ArrayList<>();
        } catch (Exception e) {
            log.error("Fail-soft in SemanticSearchTool: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
