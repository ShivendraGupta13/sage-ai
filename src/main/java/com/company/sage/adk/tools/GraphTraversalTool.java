package com.company.sage.adk.tools;

import com.company.sage.adk.agents.QueryInterpretationParser;
import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.SageProperties;
import com.company.sage.model.GraphRetrieveRequest;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
import com.google.adk.tools.Annotations;
import com.google.adk.tools.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GraphTraversalTool {

    private static final Logger log = LoggerFactory.getLogger(GraphTraversalTool.class);

    private final GraphRagClient graphRagClient;
    private final SageProperties properties;

    public GraphTraversalTool(GraphRagClient graphRagClient, SageProperties properties) {
        this.graphRagClient = graphRagClient;
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public List<RetrieveHit> graphTraversal(
        @Annotations.Schema(name = "techNeeded", description = "Technology or pattern tags extracted from query interpretation") List<String> techNeeded,
        ToolContext toolContext
    ) {
        List<String> effectiveTech = techNeeded;
        if ((effectiveTech == null || effectiveTech.isEmpty()) && toolContext != null && toolContext.state() != null) {
            Object fromState = toolContext.state().get("tech_needed");
            if (fromState instanceof List<?> l && !l.isEmpty()) {
                effectiveTech = (List<String>) l;
            } else {
                // Post-review enhancement: Fallback to reading raw query_interpretation session state key if argument is blank
                Object rawInterp = toolContext.state().get("query_interpretation");
                if (rawInterp instanceof String s && !s.isBlank()) {
                    effectiveTech = QueryInterpretationParser.parse(s, "").techNeeded();
                }
            }
        }
        if (effectiveTech == null) {
            effectiveTech = List.of();
        }

        String correlationId = null;
        if (toolContext != null && toolContext.state() != null) {
            Object corr = toolContext.state().get("correlationId");
            if (corr instanceof String s) {
                correlationId = s;
            }
        }

        int topK = properties.retrieval().topK();

        log.info("Executing graphTraversal for techNeeded: {}, topK: {}, correlationId: {}",
            effectiveTech, topK, correlationId);

        GraphRetrieveRequest request = new GraphRetrieveRequest(effectiveTech, topK);
        RetrieveResponse response = graphRagClient.retrieveGraph(request, correlationId);

        List<RetrieveHit> hits = (response != null && response.hits() != null) ? response.hits() : List.of();

        if (toolContext != null && toolContext.state() != null) {
            toolContext.state().put("graph_hits", hits);
        }

        return hits;
    }
}
