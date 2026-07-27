package com.company.sage.merge;

import com.company.sage.config.SageProperties;
import com.company.sage.model.CardResult;
import com.company.sage.model.PersonMetadata;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveMetadata;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ResultMerger {

    public List<CardResult> merge(
        List<RetrieveHit> semanticHits,
        List<RetrieveHit> graphHits,
        SageProperties.Scoring scoring,
        int topK
    ) {
        if (scoring == null) {
            scoring = new SageProperties.Scoring(0.6, 0.4, 0.1, 0.60);
        }
        return merge(
            semanticHits,
            graphHits,
            scoring.w1(),
            scoring.w2(),
            scoring.dualMatchBoost(),
            scoring.minScore(),
            topK
        );
    }

    public List<CardResult> merge(
        List<RetrieveHit> semanticHits,
        List<RetrieveHit> graphHits,
        double w1,
        double w2,
        double dualMatchBoost,
        double minScore,
        int topK
    ) {
        List<RetrieveHit> semList = (semanticHits != null) ? semanticHits : List.of();
        List<RetrieveHit> graphList = (graphHits != null) ? graphHits : List.of();

        if (semList.isEmpty() && graphList.isEmpty()) {
            return List.of();
        }

        Map<String, RetrieveHit> semMap = new LinkedHashMap<>();
        for (RetrieveHit hit : semList) {
            if (hit != null && hit.docId() != null) {
                semMap.putIfAbsent(hit.docId(), hit);
            }
        }

        Map<String, RetrieveHit> graphMap = new LinkedHashMap<>();
        for (RetrieveHit hit : graphList) {
            if (hit != null && hit.docId() != null) {
                graphMap.putIfAbsent(hit.docId(), hit);
            }
        }

        Set<String> allDocIds = new LinkedHashSet<>();
        allDocIds.addAll(semMap.keySet());
        allDocIds.addAll(graphMap.keySet());

        List<CardResultCandidate> candidates = new ArrayList<>();

        for (String docId : allDocIds) {
            RetrieveHit semHit = semMap.get(docId);
            RetrieveHit gHit = graphMap.get(docId);

            boolean isSemantic = semHit != null;
            boolean isGraph = gHit != null;

            List<String> matchedVia = new ArrayList<>();
            if (isSemantic) matchedVia.add("semantic");
            if (isGraph) matchedVia.add("graph");

            double vecScore = (isSemantic && semHit.vectorScore() != null) ? semHit.vectorScore() : 0.0;
            double gScore = (isGraph && gHit.graphScore() != null) ? gHit.graphScore() : 0.0;

            boolean dualMatched = isSemantic && isGraph;
            double rawScore = (w1 * vecScore) + (w2 * gScore) + (dualMatched ? dualMatchBoost : 0.0);
            double clamped = Math.min(1.0, Math.max(0.0, rawScore));
            double confidenceScore = Math.round(clamped * 100.0) / 100.0;

            if (confidenceScore < minScore) {
                continue;
            }

            RetrieveHit primaryHit = isSemantic ? semHit : gHit;
            RetrieveMetadata meta = primaryHit.metadata();

            String hardProblemTitle = (meta != null && meta.title() != null) ? meta.title() : "Untitled";
            String teamName = (meta != null && meta.teamName() != null) ? meta.teamName() : "N/A";
            String category = (meta != null && meta.category() != null) ? meta.category() : "HARD_PROBLEMS";
            String documentLink = (meta != null) ? meta.getFirstDocumentLink() : null;

            Set<String> solvedByNames = new LinkedHashSet<>();
            if (semHit != null && semHit.metadata() != null && semHit.metadata().people() != null) {
                semHit.metadata().people().stream().map(PersonMetadata::name).filter(Objects::nonNull).forEach(solvedByNames::add);
            }
            if (gHit != null && gHit.metadata() != null && gHit.metadata().people() != null) {
                gHit.metadata().people().stream().map(PersonMetadata::name).filter(Objects::nonNull).forEach(solvedByNames::add);
            }
            List<String> solvedBy = new ArrayList<>(solvedByNames);

            String summary = primaryHit.passage();
            if (summary == null && meta != null) {
                summary = meta.title();
            }
            if (summary == null) {
                summary = "";
            }

            List<String> matchedTags = (isGraph && gHit.matchedTags() != null) ? gHit.matchedTags() : List.of();
            String tagDesc = matchedTags.isEmpty() ? "graph path" : String.join(", ", matchedTags);

            String evidenceDetail;
            if (dualMatched) {
                evidenceDetail = String.format(Locale.ROOT, "Semantic similarity %.2f; graph matched via tag(s) %s", vecScore, tagDesc);
            } else if (isSemantic) {
                evidenceDetail = String.format(Locale.ROOT, "Semantic similarity %.2f", vecScore);
            } else {
                evidenceDetail = String.format(Locale.ROOT, "Graph matched via tag(s) %s", tagDesc);
            }

            Set<String> sources = new LinkedHashSet<>();
            if (semHit != null && semHit.metadata() != null && semHit.metadata().sourceAttribution() != null) {
                sources.add(semHit.metadata().sourceAttribution());
            }
            if (gHit != null && gHit.metadata() != null && gHit.metadata().sourceAttribution() != null) {
                sources.add(gHit.metadata().sourceAttribution());
            }
            if (sources.isEmpty()) {
                sources.add("Orion API");
            }
            List<String> sourceAttribution = new ArrayList<>(sources);

            candidates.add(new CardResultCandidate(
                docId, rawScore, confidenceScore, matchedVia, teamName, hardProblemTitle, category,
                solvedBy, summary, documentLink, evidenceDetail, sourceAttribution
            ));
        }

        candidates.sort(Comparator.comparing(CardResultCandidate::rawScore).reversed()
            .thenComparing(CardResultCandidate::docId));

        int limit = Math.min(candidates.size(), Math.max(1, topK));
        List<CardResult> finalResults = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            CardResultCandidate c = candidates.get(i);
            finalResults.add(new CardResult(
                i + 1, c.confidenceScore, c.matchedVia, c.teamName, c.hardProblemTitle,
                c.category, c.solvedBy, c.summary, c.documentLink, c.evidenceDetail, c.sourceAttribution
            ));
        }

        return finalResults;
    }

    private record CardResultCandidate(
        String docId,
        double rawScore,
        double confidenceScore,
        List<String> matchedVia,
        String teamName,
        String hardProblemTitle,
        String category,
        List<String> solvedBy,
        String summary,
        String documentLink,
        String evidenceDetail,
        List<String> sourceAttribution
    ) {}
}
