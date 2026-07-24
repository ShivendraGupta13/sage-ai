package com.company.sage.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.sage.model.RetrieveHit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolArgsTest {

    @Test
    void asStringList_acceptsList() {
        assertThat(ToolArgs.asStringList(List.of("OpenTelemetry", "Kafka")))
                .containsExactly("OpenTelemetry", "Kafka");
    }

    @Test
    void asStringList_acceptsJsonArrayString() {
        assertThat(ToolArgs.asStringList("[\"OpenTelemetry\", \"Prometheus\"]"))
                .containsExactly("OpenTelemetry", "Prometheus");
    }

    @Test
    void asStringList_acceptsCommaSeparatedString() {
        assertThat(ToolArgs.asStringList("Microservices, Kafka, EC2"))
                .containsExactly("Microservices", "Kafka", "EC2");
    }

    @Test
    void asStringList_returnsEmptyForNullAndBlank() {
        assertThat(ToolArgs.asStringList(null)).isEmpty();
        assertThat(ToolArgs.asStringList("   ")).isEmpty();
    }

    @Test
    void asStringList_returnsEmptyForInvalidJsonArray() {
        assertThat(ToolArgs.asStringList("[not-json")).isEmpty();
    }

    @Test
    void asHitList_acceptsListOfMaps() {
        List<RetrieveHit> hits = ToolArgs.asHitList(List.of(
                Map.of("doc_id", "101", "source", "orion_metadata", "passage", "Kafka flush"),
                Map.of("doc_id", "202", "source", "orion_metadata", "passage", "OpenTelemetry")));

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).getDocId()).isEqualTo("101");
        assertThat(hits.get(1).getDocId()).isEqualTo("202");
    }

    @Test
    void asHitList_acceptsJsonArrayString() {
        List<RetrieveHit> hits = ToolArgs.asHitList(
                "[{\"doc_id\":\"178025\",\"source\":\"orion_metadata\",\"passage\":\"spot termination\"}]");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getDocId()).isEqualTo("178025");
    }

    @Test
    void asHitList_unwrapsAdkResultWrapper() {
        List<RetrieveHit> hits = ToolArgs.asHitList(Map.of(
                "result",
                List.of(Map.of("doc_id", "55", "source", "orion_metadata", "passage", "x"))));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getDocId()).isEqualTo("55");
    }

    @Test
    void asHitList_returnsEmptyForNullAndNonArrayString() {
        assertThat(ToolArgs.asHitList(null)).isEmpty();
        assertThat(ToolArgs.asHitList("not-an-array")).isEmpty();
    }

    @Test
    void flexibleStringList_bindsFromArrayAndString() {
        assertThat(FlexibleStringList.from(List.of("a", "b")).asList()).containsExactly("a", "b");
        assertThat(FlexibleStringList.from("[\"a\",\"b\"]").asList()).containsExactly("a", "b");
        assertThat(FlexibleStringList.from("a, b").asList()).containsExactly("a", "b");
    }
}
