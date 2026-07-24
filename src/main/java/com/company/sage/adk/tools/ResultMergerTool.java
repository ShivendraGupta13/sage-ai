package com.company.sage.adk.tools;

import com.company.sage.config.RetrievalProperties;
import com.company.sage.config.ScoringProperties;
import com.company.sage.merge.ResultMerger;
import com.company.sage.merge.ScoringConfig;
import com.company.sage.model.MergedHit;
import com.company.sage.model.RetrieveHit;
import com.company.sage.util.ToolArgs;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ADK Tool wrapper for the deterministic result merging and scoring logic.
 * Reads {@code semantic_hits} and {@code graph_hits} written by retrieve tools into
 * session state (not ADK {@code outputKey} text), then writes {@code merged_hits}.
 */
@Component
public class ResultMergerTool {

    private static final Logger log = LoggerFactory.getLogger(ResultMergerTool.class);

    private final ScoringProperties scoringProperties;
    private final RetrievalProperties retrievalProperties;

    public ResultMergerTool(ScoringProperties scoringProperties, RetrievalProperties retrievalProperties) {
        this.scoringProperties = scoringProperties;
        this.retrievalProperties = retrievalProperties;
    }

    /**
     * Combines, deduplicates, filters, and ranks retrieve hits from session state.
     */
    public List<MergedHit> merge(ToolContext toolContext) {
        List<RetrieveHit> sem = List.of();
        List<RetrieveHit> graph = List.of();
        if (toolContext != null && toolContext.state() != null) {
            sem = ToolArgs.asHitList(toolContext.state().get("semantic_hits"));
            graph = ToolArgs.asHitList(toolContext.state().get("graph_hits"));
        }

        ScoringConfig config = new ScoringConfig(
                scoringProperties.getW1(),
                scoringProperties.getW2(),
                scoringProperties.getDualMatchBoost(),
                scoringProperties.getMinScore(),
                retrievalProperties.getTopK()
        );

        try {
            ResultMerger resultMerger = new ResultMerger();
            List<MergedHit> merged = resultMerger.merge(sem, graph, config);
            if (toolContext != null) {
                toolContext.state().put("merged_hits", merged);
            }
            return merged;
        } catch (Exception e) {
            log.error("Fail-soft in ResultMergerTool: {}", e.getMessage());
            List<MergedHit> empty = new ArrayList<>();
            if (toolContext != null) {
                toolContext.state().put("merged_hits", empty);
            }
            return empty;
        }
    }
}
