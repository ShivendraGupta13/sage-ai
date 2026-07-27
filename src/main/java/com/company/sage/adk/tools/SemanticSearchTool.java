package com.company.sage.adk.tools;

import com.company.sage.adk.agents.QueryInterpretationParser;
import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.SageProperties;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
import com.company.sage.model.SemanticRetrieveRequest;
import com.google.adk.tools.Annotations;
import com.google.adk.tools.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SemanticSearchTool {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchTool.class);

    private final GraphRagClient graphRagClient;
    private final SageProperties properties;

    public SemanticSearchTool(GraphRagClient graphRagClient, SageProperties properties) {
        this.graphRagClient = graphRagClient;
        this.properties = properties;
    }

    public List<RetrieveHit> semanticSearch(
        @Annotations.Schema(name = "problemStatement", description = "Problem statement extracted from query interpretation") String problemStatement,
        ToolContext toolContext
    ) {
        String effectiveStatement = problemStatement;
        if ((effectiveStatement == null || effectiveStatement.isBlank()) && toolContext != null && toolContext.state() != null) {
            Object fromState = toolContext.state().get("problem_statement");
            if (fromState instanceof String s && !s.isBlank()) {
                effectiveStatement = s;
            } else {
                // Post-review enhancement: Fallback to reading raw query_interpretation session state key if argument is blank
                Object rawInterp = toolContext.state().get("query_interpretation");
                if (rawInterp instanceof String s && !s.isBlank()) {
                    effectiveStatement = QueryInterpretationParser.parse(s, "").problemStatement();
                }
            }
        }
        if (effectiveStatement == null) {
            effectiveStatement = "";
        }

        String correlationId = null;
        if (toolContext != null && toolContext.state() != null) {
            Object corr = toolContext.state().get("correlationId");
            if (corr instanceof String s) {
                correlationId = s;
            }
        }

        int topK = properties.retrieval().topK();
        double minScore = properties.retrieval().minScore();

        log.info("Executing semanticSearch for problemStatement: '{}', topK: {}, minScore: {}, correlationId: {}",
            effectiveStatement, topK, minScore, correlationId);

        SemanticRetrieveRequest request = new SemanticRetrieveRequest(effectiveStatement, topK, minScore, false);
        RetrieveResponse response = graphRagClient.retrieveSemantic(request, correlationId);

        List<RetrieveHit> hits = (response != null && response.hits() != null) ? response.hits() : List.of();

        if (toolContext != null && toolContext.state() != null) {
            toolContext.state().put("semantic_hits", hits);
        }

        return hits;
    }
}
