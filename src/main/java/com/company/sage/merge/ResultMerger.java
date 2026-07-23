package com.company.sage.merge;

import com.company.sage.model.MergedHit;
import com.company.sage.model.RetrieveHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic merge: deduplicate by hard-problem identity, score, sort descending, and apply top-k.
 */
public final class ResultMerger {

    /**
     * Merges and ranks retrieval hits from semantic and graph search paths.
     *
     * @param semantic List of semantic retrieval hits.
     * @param graph List of graph retrieval hits.
     * @param cfg Scoring configuration.
     * @return Ranked list of merged hits.
     */
    public List<MergedHit> merge(List<RetrieveHit> semantic, List<RetrieveHit> graph, ScoringConfig cfg) {
        if (semantic == null) {
            semantic = List.of();
        }
        if (graph == null) {
            graph = List.of();
        }

        Map<String, RetrieveHit> semMap = new HashMap<>();
        for (RetrieveHit hit : semantic) {
            if (hit != null && hit.getDocId() != null) {
                semMap.put(hit.getDocId(), hit);
            }
        }

        Map<String, RetrieveHit> graphMap = new HashMap<>();
        for (RetrieveHit hit : graph) {
            if (hit != null && hit.getDocId() != null) {
                graphMap.put(hit.getDocId(), hit);
            }
        }

        Set<String> allDocIds = new HashSet<>();
        allDocIds.addAll(semMap.keySet());
        allDocIds.addAll(graphMap.keySet());

        List<MergedHit> mergedHits = new ArrayList<>();

        for (String docId : allDocIds) {
            RetrieveHit semHit = semMap.get(docId);
            RetrieveHit graphHit = graphMap.get(docId);

            boolean isSemantic = semHit != null;
            boolean isGraph = graphHit != null;

            double rawScore = 0.0;
            List<String> matchedVia = new ArrayList<>();
            RetrieveHit referenceHit;

            if (isSemantic && isGraph) {
                matchedVia.add("semantic");
                matchedVia.add("graph");
                referenceHit = semHit; // Default to semantic metadata

                double vectorVal = semHit.getVectorScore() != null ? semHit.getVectorScore() : 0.0;
                double graphVal = graphHit.getGraphScore() != null ? graphHit.getGraphScore() : 0.0;
                rawScore = (cfg.getW1() * vectorVal) + (cfg.getW2() * graphVal) + cfg.getDualMatchBoost();
            } else if (isSemantic) {
                matchedVia.add("semantic");
                referenceHit = semHit;

                double vectorVal = semHit.getVectorScore() != null ? semHit.getVectorScore() : 0.0;
                rawScore = cfg.getW1() * vectorVal;
            } else {
                matchedVia.add("graph");
                referenceHit = graphHit;

                double graphVal = graphHit.getGraphScore() != null ? graphHit.getGraphScore() : 0.0;
                rawScore = cfg.getW2() * graphVal;
            }

            double confidenceScore = Math.min(1.0, Math.max(0.0, rawScore));

            // Generate evidenceDetail
            String evidenceDetail;
            if (isSemantic && isGraph) {
                double vectorVal = semHit.getVectorScore() != null ? semHit.getVectorScore() : 0.0;
                List<String> matchedTags = graphHit.getMatchedTags();
                if (matchedTags != null && !matchedTags.isEmpty()) {
                    evidenceDetail = String.format("Semantic similarity %.2f; graph matched via tags: %s",
                            vectorVal, String.join(", ", matchedTags));
                } else {
                    evidenceDetail = String.format("Semantic similarity %.2f; graph match", vectorVal);
                }
            } else if (isSemantic) {
                double vectorVal = semHit.getVectorScore() != null ? semHit.getVectorScore() : 0.0;
                evidenceDetail = String.format("Semantic similarity %.2f", vectorVal);
            } else {
                List<String> matchedTags = graphHit.getMatchedTags();
                if (matchedTags != null && !matchedTags.isEmpty()) {
                    evidenceDetail = String.format("Graph matched via tags: %s", String.join(", ", matchedTags));
                } else {
                    evidenceDetail = "Graph match";
                }
            }

            // Map metadata fields
            String teamName = "N/A";
            String title = "";
            String category = "HARD_PROBLEMS";
            String docLink = null;
            List<String> solvedBy = List.of();
            List<String> sourceAttribution = List.of("Orion API");

            if (referenceHit.getMetadata() != null) {
                var meta = referenceHit.getMetadata();
                if (meta.getTeamName() != null) {
                    teamName = meta.getTeamName();
                }
                if (meta.getTitle() != null) {
                    title = meta.getTitle();
                }
                if (meta.getCategory() != null) {
                    category = meta.getCategory();
                }
                docLink = meta.getDocumentLink();
                if (meta.getPeople() != null) {
                    solvedBy = meta.getPeople().stream()
                            .map(p -> p.getName())
                            .filter(name -> name != null)
                            .collect(Collectors.toList());
                }
                if (meta.getSourceAttribution() != null) {
                    sourceAttribution = List.of(meta.getSourceAttribution());
                }
            }

            String summary = referenceHit.getPassage() != null ? referenceHit.getPassage() : "";

            MergedHit mergedHit = new MergedHit(
                    docId,
                    0, // Assigned below
                    confidenceScore,
                    matchedVia,
                    teamName,
                    title,
                    category,
                    solvedBy,
                    summary,
                    docLink,
                    evidenceDetail,
                    sourceAttribution
            );

            mergedHits.add(mergedHit);
        }

        // Filter by threshold, sort desc, limit to topK
        List<MergedHit> filteredHits = mergedHits.stream()
                .filter(hit -> hit.getConfidenceScore() >= cfg.getMinScore())
                .sorted(Comparator.comparingDouble(MergedHit::getConfidenceScore).reversed())
                .limit(cfg.getTopK())
                .collect(Collectors.toList());

        // Assign 1-based ranks
        for (int i = 0; i < filteredHits.size(); i++) {
            filteredHits.get(i).setRank(i + 1);
        }

        return filteredHits;
    }
}
