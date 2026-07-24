package com.company.sage.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.company.sage.model.MergedHit;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Merges local recorded semantic + graph fixtures from happy-path-queries.json.
 * Skips when local fixtures are absent (entire {@code fixtures/graphrag/} is gitignored).
 */
class ResultMergerRecordedFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HAPPY_PATH = "/fixtures/graphrag/happy-path-queries.json";
    private final ResultMerger merger = new ResultMerger();

    @Test
    void mergeFirstHappyPathBothPathsProducesRankedKnowledgeCardFields() throws Exception {
        assumeTrue(resourceExists(HAPPY_PATH), "No local Graph RAG fixtures; skip recorded merge test");
        String fixtureDir = firstBothPathsFixtureDir();
        assumeTrue(
                resourceExists(fixtureDir + "semantic.response.json")
                        && resourceExists(fixtureDir + "graph.response.json"),
                "No local recorded fixtures; skip recorded merge test");

        RetrieveResponse semantic = readJson(fixtureDir + "semantic.response.json", RetrieveResponse.class);
        RetrieveResponse graph = readJson(fixtureDir + "graph.response.json", RetrieveResponse.class);

        ScoringConfig cfg = new ScoringConfig(0.6, 0.4, 0.1, 0.60, 5);
        List<MergedHit> merged = merger.merge(semantic.getHits(), graph.getHits(), cfg);

        assertThat(merged).isNotEmpty();
        MergedHit top = merged.get(0);
        assertThat(top.getRank()).isEqualTo(1);
        assertThat(top.getDocId()).isNotBlank();
        assertThat(top.getHardProblemTitle()).isNotBlank();
        assertThat(top.getTeamName()).isNotBlank();
        assertThat(top.getSummary()).isNotBlank();
        assertThat(top.getCategory()).isEqualTo("HARD_PROBLEMS");
        assertThat(top.getMatchedVia()).isNotEmpty();
        assertThat(top.getConfidenceScore()).isGreaterThan(0.0);
        assertThat(top.getSourceAttribution()).isNotEmpty();

        boolean anyDual = merged.stream().anyMatch(h -> h.getMatchedVia().contains("semantic")
                && h.getMatchedVia().contains("graph"));
        assertThat(anyDual).as("expected at least one dual-path hit").isTrue();

        String dualDocId = semantic.getHits().stream()
                .map(RetrieveHit::getDocId)
                .filter(id -> graph.getHits().stream().anyMatch(g -> id.equals(g.getDocId())))
                .findFirst()
                .orElseThrow();
        MergedHit dual = merged.stream()
                .filter(h -> dualDocId.equals(h.getDocId()))
                .findFirst()
                .orElseThrow();
        assertThat(dual.getMatchedVia()).containsExactlyInAnyOrder("semantic", "graph");
        assertThat(dual.getHardProblemTitle()).isNotBlank();
    }

    private static String firstBothPathsFixtureDir() throws Exception {
        JsonNode root = readTree(HAPPY_PATH);
        for (JsonNode query : root.path("queries")) {
            if ("both-paths".equals(query.path("label").asText())) {
                return "/fixtures/graphrag/" + query.path("fixtureDir").asText() + "/";
            }
        }
        throw new IllegalStateException("No both-paths entry in happy-path-queries.json");
    }

    private static boolean resourceExists(String classpath) {
        try (InputStream in = ResultMergerRecordedFixtureTest.class.getResourceAsStream(classpath)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonNode readTree(String classpath) throws Exception {
        try (InputStream in = ResultMergerRecordedFixtureTest.class.getResourceAsStream(classpath)) {
            assertThat(in).as("classpath resource %s", classpath).isNotNull();
            return MAPPER.readTree(in);
        }
    }

    private <T> T readJson(String classpath, Class<T> type) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("classpath resource %s", classpath).isNotNull();
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return MAPPER.readValue(json, type);
        }
    }
}
