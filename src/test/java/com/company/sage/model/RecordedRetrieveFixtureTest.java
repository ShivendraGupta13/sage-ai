package com.company.sage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Deserializes local recorded Graph RAG fixtures listed in happy-path-queries.json.
 * Skips when local fixtures are absent (entire {@code fixtures/graphrag/} is gitignored).
 */
class RecordedRetrieveFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HAPPY_PATH = "/fixtures/graphrag/happy-path-queries.json";

    static Stream<String> happyPathFixtureDirs() throws Exception {
        assumeTrue(resourceExists(HAPPY_PATH), "No local Graph RAG fixtures; skip recorded deserialize tests");
        JsonNode root = readTree(HAPPY_PATH);
        List<String> dirs = new ArrayList<>();
        for (JsonNode query : root.path("queries")) {
            String fixtureDir = query.path("fixtureDir").asText(null);
            assertThat(fixtureDir).isNotBlank();
            String classpathDir = "/fixtures/graphrag/" + fixtureDir + "/";
            if (resourceExists(classpathDir + "semantic.response.json")) {
                dirs.add(classpathDir);
            }
        }
        assumeTrue(!dirs.isEmpty(), "No local recorded fixtures; skip recorded deserialize tests");
        return dirs.stream();
    }

    @ParameterizedTest
    @MethodSource("happyPathFixtureDirs")
    void recordedSemanticResponseDeserializes(String fixtureDir) throws Exception {
        RetrieveResponse response = readJson(fixtureDir + "semantic.response.json", RetrieveResponse.class);

        assertThat(response.getHits()).isNotEmpty();
        assertThat(response.getTotalFound()).isGreaterThan(0);
        RetrieveHit hit = response.getHits().get(0);
        assertThat(hit.getDocId()).isNotBlank();
        assertThat(hit.getVectorScore()).isNotNull();
        assertThat(hit.getPassage()).isNotBlank();
        assertThat(hit.getMetadata()).isNotNull();
        assertThat(hit.getMetadata().getTitle()).isNotBlank();
        assertThat(hit.getMetadata().getTeamName()).isNotBlank();
        assertThat(hit.getMetadata().getCategory()).isEqualTo("HARD_PROBLEMS");
    }

    @ParameterizedTest
    @MethodSource("happyPathFixtureDirs")
    void recordedGraphResponseDeserializes(String fixtureDir) throws Exception {
        RetrieveResponse response = readJson(fixtureDir + "graph.response.json", RetrieveResponse.class);

        assertThat(response.getHits()).isNotEmpty();
        RetrieveHit hit = response.getHits().get(0);
        assertThat(hit.getDocId()).isNotBlank();
        assertThat(hit.getGraphScore()).isNotNull();
        assertThat(hit.getMatchedTags()).isNotEmpty();
        assertThat(hit.getPathDescription()).isNotBlank();
        assertThat(hit.getMetadata().getTitle()).isNotBlank();
    }

    private static boolean resourceExists(String classpath) {
        try (InputStream in = RecordedRetrieveFixtureTest.class.getResourceAsStream(classpath)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonNode readTree(String classpath) throws Exception {
        try (InputStream in = RecordedRetrieveFixtureTest.class.getResourceAsStream(classpath)) {
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
