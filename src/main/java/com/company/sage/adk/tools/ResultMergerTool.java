package com.company.sage.adk.tools;

import com.company.sage.config.SageProperties;
import com.company.sage.merge.ResultMerger;
import com.company.sage.model.CardResult;
import com.company.sage.model.RetrieveHit;
import com.google.adk.tools.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResultMergerTool {

    private static final Logger log = LoggerFactory.getLogger(ResultMergerTool.class);

    private final ResultMerger resultMerger;
    private final SageProperties properties;

    public ResultMergerTool(ResultMerger resultMerger, SageProperties properties) {
        this.resultMerger = resultMerger;
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public List<CardResult> merge(ToolContext toolContext) {
        List<RetrieveHit> semanticHits = List.of();
        List<RetrieveHit> graphHits = List.of();

        if (toolContext != null && toolContext.state() != null) {
            Object sHits = toolContext.state().get("semantic_hits");
            if (sHits instanceof List<?> list) {
                semanticHits = (List<RetrieveHit>) list;
            }

            Object gHits = toolContext.state().get("graph_hits");
            if (gHits instanceof List<?> list) {
                graphHits = (List<RetrieveHit>) list;
            }
        }

        log.info("Merging retrieval hits: {} semantic hits, {} graph hits", semanticHits.size(), graphHits.size());

        SageProperties.Scoring scoring = properties.scoring();
        int topK = properties.retrieval().topK();

        List<CardResult> mergedHits = resultMerger.merge(semanticHits, graphHits, scoring, topK);

        if (toolContext != null && toolContext.state() != null) {
            toolContext.state().put("merged_hits", mergedHits);
        }

        return mergedHits;
    }
}
