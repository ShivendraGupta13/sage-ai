package com.company.sage.adk.tools;

import com.company.sage.config.RetrievalProperties;
import com.company.sage.config.ScoringProperties;
import com.company.sage.merge.ResultMerger;
import com.company.sage.merge.ScoringConfig;
import com.company.sage.model.MergedHit;
import com.company.sage.model.RetrieveHit;
import com.google.adk.tools.Annotations.Schema;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ADK Tool wrapper for the deterministic result merging and scoring logic.
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
     * Combines, deduplicates, filters, and ranks retrieve hits.
     */
    public List<MergedHit> merge(
            @Schema(name = "semanticHits", description = "The fanned hits from semantic search", optional = true)
            List<RetrieveHit> semanticHits,
            @Schema(name = "graphHits", description = "The fanned hits from graph search", optional = true)
            List<RetrieveHit> graphHits) {

        List<RetrieveHit> sem = semanticHits != null ? semanticHits : new ArrayList<>();
        List<RetrieveHit> graph = graphHits != null ? graphHits : new ArrayList<>();

        ScoringConfig config = new ScoringConfig(
                scoringProperties.getW1(),
                scoringProperties.getW2(),
                scoringProperties.getDualMatchBoost(),
                scoringProperties.getMinScore(),
                retrievalProperties.getTopK()
        );

        try {
            ResultMerger resultMerger = new ResultMerger();
            return resultMerger.merge(sem, graph, config);
        } catch (Exception e) {
            log.error("Fail-soft in ResultMergerTool: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
