package com.company.sage.merge;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.sage.model.MergedHit;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveHitMetadata;
import com.company.sage.model.RetrievePerson;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultMergerTest {

    private final ResultMerger merger = new ResultMerger();

    @Test
    void testMergeEmptyInputs() {
        ScoringConfig cfg = new ScoringConfig(0.6, 0.4, 0.1, 0.60, 5);

        List<MergedHit> resultNulls = merger.merge(null, null, cfg);
        assertThat(resultNulls).isNotNull().isEmpty();

        List<MergedHit> resultEmpty = merger.merge(List.of(), List.of(), cfg);
        assertThat(resultEmpty).isNotNull().isEmpty();
    }

    @Test
    void testMergeSemanticOnly() {
        ScoringConfig cfg = new ScoringConfig(0.6, 0.4, 0.1, 0.50, 5);

        RetrieveHitMetadata meta = new RetrieveHitMetadata("Title 1", "Team 1", "1", List.of(), List.of(), null, "HARD_PROBLEMS", "Orion API");
        RetrieveHit semHit = new RetrieveHit("doc1", "orion_metadata", 0.90, null, null, null, "Passage 1", meta);

        List<MergedHit> results = merger.merge(List.of(semHit), List.of(), cfg);

        assertThat(results).hasSize(1);
        MergedHit merged = results.get(0);
        assertThat(merged.getDocId()).isEqualTo("doc1");
        // w1 * vectorScore = 0.6 * 0.90 = 0.54
        assertThat(merged.getConfidenceScore()).isCloseTo(0.54, org.assertj.core.data.Offset.offset(0.001));
        assertThat(merged.getMatchedVia()).containsExactly("semantic");
        assertThat(merged.getRank()).isEqualTo(1);
        assertThat(merged.getEvidenceDetail()).isEqualTo("Semantic similarity 0.90");
    }

    @Test
    void testMergeGraphOnly() {
        ScoringConfig cfg = new ScoringConfig(0.6, 0.4, 0.1, 0.30, 5);

        RetrieveHitMetadata meta = new RetrieveHitMetadata("Title 2", "Team 2", "2", List.of(), List.of(), null, "HARD_PROBLEMS", "Orion API");
        RetrieveHit graphHit = new RetrieveHit("doc2", "orion_metadata", null, 0.80, List.of("npm", "Node.js"), "Path desc", "Passage 2", meta);

        List<MergedHit> results = merger.merge(List.of(), List.of(graphHit), cfg);

        assertThat(results).hasSize(1);
        MergedHit merged = results.get(0);
        assertThat(merged.getDocId()).isEqualTo("doc2");
        // w2 * graphScore = 0.4 * 0.80 = 0.32
        assertThat(merged.getConfidenceScore()).isCloseTo(0.32, org.assertj.core.data.Offset.offset(0.001));
        assertThat(merged.getMatchedVia()).containsExactly("graph");
        assertThat(merged.getRank()).isEqualTo(1);
        assertThat(merged.getEvidenceDetail()).isEqualTo("Graph matched via tags: npm, Node.js");
    }

    @Test
    void testMergeDualPathBoostAndDeduplication() {
        ScoringConfig cfg = new ScoringConfig(0.6, 0.4, 0.1, 0.50, 5);

        RetrieveHitMetadata metaSem = new RetrieveHitMetadata("Title 1", "Team 1", "1", List.of(new RetrievePerson("65", "Priya Sharma")), List.of(), null, "HARD_PROBLEMS", "Orion API");
        RetrieveHit semHit = new RetrieveHit("doc1", "orion_metadata", 0.80, null, null, null, "Passage 1", metaSem);

        RetrieveHitMetadata metaGraph = new RetrieveHitMetadata("Title 1", "Team 1", "1", List.of(new RetrievePerson("65", "Priya Sharma")), List.of(), null, "HARD_PROBLEMS", "Orion API");
        RetrieveHit graphHit = new RetrieveHit("doc1", "orion_metadata", null, 0.70, List.of("SSRF"), "Path desc", "Passage 1", metaGraph);

        List<MergedHit> results = merger.merge(List.of(semHit), List.of(graphHit), cfg);

        assertThat(results).hasSize(1);
        MergedHit merged = results.get(0);
        assertThat(merged.getDocId()).isEqualTo("doc1");
        // w1 * vectorScore + w2 * graphScore + dualMatchBoost = 0.6 * 0.80 + 0.4 * 0.70 + 0.1 = 0.48 + 0.28 + 0.1 = 0.86
        assertThat(merged.getConfidenceScore()).isCloseTo(0.86, org.assertj.core.data.Offset.offset(0.001));
        assertThat(merged.getMatchedVia()).containsExactlyInAnyOrder("semantic", "graph");
        assertThat(merged.getSolvedBy()).containsExactly("Priya Sharma");
        assertThat(merged.getEvidenceDetail()).isEqualTo("Semantic similarity 0.80; graph matched via tags: SSRF");
    }

    @Test
    void testMergeThresholdDropAndTopK() {
        ScoringConfig cfg = new ScoringConfig(0.6, 0.4, 0.1, 0.60, 2);

        RetrieveHitMetadata meta = new RetrieveHitMetadata("Title", "Team", "1", List.of(), List.of(), null, "HARD_PROBLEMS", "Orion API");

        RetrieveHit semHit1 = new RetrieveHit("doc1", "source", 0.90, null, null, null, "Passage", meta);
        RetrieveHit semHit2 = new RetrieveHit("doc2", "source", 1.00, null, null, null, "Passage", meta);

        RetrieveHit semHit3 = new RetrieveHit("doc3", "source", 0.80, null, null, null, "Passage", meta);
        RetrieveHit graphHit3 = new RetrieveHit("doc3", "source", null, 0.80, List.of(), "", "Passage", meta);

        RetrieveHit graphHit4 = new RetrieveHit("doc4", "source", null, 0.90, List.of(), "", "Passage", meta);
        RetrieveHit semHit5 = new RetrieveHit("doc5", "source", 0.95, null, null, null, "Passage", meta);
        RetrieveHit semHit6 = new RetrieveHit("doc6", "source", 1.00, null, null, null, "Passage", meta);

        List<RetrieveHit> semanticList = List.of(semHit1, semHit2, semHit3, semHit5, semHit6);
        List<RetrieveHit> graphList = List.of(graphHit3, graphHit4);

        List<MergedHit> results = merger.merge(semanticList, graphList, cfg);

        // Max 2 elements due to topK config, and ordered by score descending (doc3: 0.90, then doc2 or doc6: 0.60)
        assertThat(results).hasSize(2);

        assertThat(results.get(0).getDocId()).isEqualTo("doc3");
        assertThat(results.get(0).getRank()).isEqualTo(1);
        assertThat(results.get(0).getConfidenceScore()).isCloseTo(0.90, org.assertj.core.data.Offset.offset(0.001));

        assertThat(results.get(1).getDocId()).isIn("doc2", "doc6");
        assertThat(results.get(1).getRank()).isEqualTo(2);
        assertThat(results.get(1).getConfidenceScore()).isCloseTo(0.60, org.assertj.core.data.Offset.offset(0.001));
    }
}
