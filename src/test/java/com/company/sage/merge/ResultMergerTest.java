package com.company.sage.merge;

import com.company.sage.config.SageProperties;
import com.company.sage.model.CardResult;
import com.company.sage.model.PersonMetadata;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResultMergerTest {

    private ResultMerger merger;
    private SageProperties.Scoring scoringConfig;

    @BeforeEach
    void setUp() {
        merger = new ResultMerger();
        // w1 = 0.6, w2 = 0.4, dualMatchBoost = 0.1, minScore = 0.60
        scoringConfig = new SageProperties.Scoring(0.6, 0.4, 0.1, 0.60);
    }

    @Test
    void shouldReturnEmptyListWhenInputsAreNullOrEmpty() {
        List<CardResult> results = merger.merge(null, null, scoringConfig, 5);
        assertThat(results).isEmpty();

        results = merger.merge(List.of(), List.of(), scoringConfig, 5);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldCalculateBoostedScoreForDualMatchedHits() {
        RetrieveMetadata metadata = new RetrieveMetadata(
            "SSRF-safe external image loader", "Payments Platform", "42",
            List.of(new PersonMetadata("65", "Priya Sharma")),
            List.of("SSRF mitigation"), List.of("https://ticket/1234"), "HARD_PROBLEMS", "Orion API"
        );

        RetrieveHit semanticHit = new RetrieveHit(
            "178025", "orion_metadata", 0.83, null, List.of(), null,
            "Implemented a server-side proxy...", metadata
        );

        RetrieveHit graphHit = new RetrieveHit(
            "178025", "orion_metadata", null, 1.0, List.of("SSRF mitigation"),
            "Path desc", "Implemented a server-side proxy...", metadata
        );

        List<CardResult> results = merger.merge(List.of(semanticHit), List.of(graphHit), scoringConfig, 5);

        assertThat(results).hasSize(1);
        CardResult card = results.getFirst();
        assertThat(card.rank()).isEqualTo(1);
        // raw = 0.6*0.83 + 0.4*1.0 + 0.1 = 0.498 + 0.4 + 0.1 = 0.998 -> rounded to 1.00 (or clamped 1.0)
        assertThat(card.confidenceScore()).isGreaterThanOrEqualTo(0.99);
        assertThat(card.matchedVia()).containsExactly("semantic", "graph");
        assertThat(card.teamName()).isEqualTo("Payments Platform");
        assertThat(card.hardProblemTitle()).isEqualTo("SSRF-safe external image loader");
        assertThat(card.solvedBy()).containsExactly("Priya Sharma");
        assertThat(card.evidenceDetail()).contains("Semantic similarity 0.83");
        assertThat(card.evidenceDetail()).contains("SSRF mitigation");
    }

    @Test
    void shouldFilterOutHitsBelowMinScoreThreshold() {
        RetrieveMetadata metadata = new RetrieveMetadata(
            "Low score doc", "Platform", "1", List.of(), List.of(), null, "HARD_PROBLEMS", "Orion API"
        );

        // vectorScore 0.50 -> 0.6 * 0.50 = 0.30, below 0.60 minScore
        RetrieveHit lowScoreHit = new RetrieveHit(
            "999", "orion_metadata", 0.50, null, List.of(), null, "Passage", metadata
        );

        List<CardResult> results = merger.merge(List.of(lowScoreHit), List.of(), scoringConfig, 5);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldSortByConfidenceScoreDescAndLimitToTopK() {
        RetrieveMetadata meta = new RetrieveMetadata("Title", "Team", "1", List.of(), List.of(), null, "HARD_PROBLEMS", "Orion API");

        // hit1: score 0.6 * 0.90 = 0.54 (below 0.60 min score without graph)
        // Let's make hit1 vectorScore 1.0 -> 0.6 * 1.0 = 0.60 (passes threshold)
        // hit2: dual match -> 0.6*0.75 + 0.4*1.0 + 0.1 = 0.45 + 0.4 + 0.1 = 0.95 (higher rank)
        RetrieveHit hit1 = new RetrieveHit("doc1", "orion", 1.0, null, List.of(), null, "Passage 1", meta);

        RetrieveHit hit2Semantic = new RetrieveHit("doc2", "orion", 0.75, null, List.of(), null, "Passage 2", meta);
        RetrieveHit hit2Graph = new RetrieveHit("doc2", "orion", null, 1.0, List.of("Tag2"), null, "Passage 2", meta);

        List<CardResult> results = merger.merge(
            List.of(hit1, hit2Semantic),
            List.of(hit2Graph),
            scoringConfig,
            1 // limit to top 1
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().rank()).isEqualTo(1);
        assertThat(results.getFirst().hardProblemTitle()).isEqualTo("Title");
        assertThat(results.getFirst().matchedVia()).containsExactly("semantic", "graph");
    }

    @Test
    void shouldHandleGraphOnlyHitCorrectly() {
        RetrieveMetadata metadata = new RetrieveMetadata(
            "Graph hit title", "Team Alpha", "10",
            List.of(new PersonMetadata("101", "Alex Developer")),
            List.of("Java"), List.of("http://link"), "HARD_PROBLEMS", "Orion API"
        );

        // graphScore = 1.0 -> 0.4 * 1.0 = 0.40 -> wait, if graph only score is below 0.60 minScore, it drops.
        // Let's test with custom scoring config where minScore = 0.35 so graph-only hits pass!
        SageProperties.Scoring customScoring = new SageProperties.Scoring(0.6, 0.4, 0.1, 0.35);

        RetrieveHit graphOnly = new RetrieveHit(
            "doc3", "orion", null, 1.0, List.of("Java"), "Path", "Passage 3", metadata
        );

        List<CardResult> results = merger.merge(List.of(), List.of(graphOnly), customScoring, 5);

        assertThat(results).hasSize(1);
        CardResult card = results.getFirst();
        assertThat(card.matchedVia()).containsExactly("graph");
        assertThat(card.confidenceScore()).isEqualTo(0.40);
        assertThat(card.evidenceDetail()).isEqualTo("Graph matched via tag(s) Java");
    }
}
