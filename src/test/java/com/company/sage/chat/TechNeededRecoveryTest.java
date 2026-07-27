package com.company.sage.chat;

import com.company.sage.model.PersonMetadata;
import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TechNeededRecoveryTest {

    @Test
    void shouldReturnEmptyWhenNoHitsOrNoTechnologies() {
        assertThat(TechNeededRecovery.fromSemanticHits(null)).isEmpty();
        assertThat(TechNeededRecovery.fromSemanticHits(List.of())).isEmpty();

        RetrieveHit noMeta = new RetrieveHit("1", "src", 1.0, null, List.of(), null, "p", null);
        assertThat(TechNeededRecovery.fromSemanticHits(List.of(noMeta))).isEmpty();

        RetrieveMetadata emptyTech = new RetrieveMetadata(
            "t", "team", "1", List.of(), List.of(), List.of(), "HARD_PROBLEMS", "Orion"
        );
        RetrieveHit empty = new RetrieveHit("2", "src", 1.0, null, List.of(), null, "p", emptyTech);
        assertThat(TechNeededRecovery.fromSemanticHits(List.of(empty))).isEmpty();
    }

    @Test
    void shouldCollectDistinctTechnologiesPreservingOrder() {
        RetrieveMetadata meta1 = new RetrieveMetadata(
            "SSRF loader", "Payments", "42",
            List.of(new PersonMetadata("1", "Priya")),
            List.of("SSRF mitigation", "Node.js"),
            List.of("https://x"), "HARD_PROBLEMS", "Orion API"
        );
        RetrieveMetadata meta2 = new RetrieveMetadata(
            "Other", "Platform", "7", List.of(),
            List.of("Node.js", "Kafka"),
            List.of(), "HARD_PROBLEMS", "Orion API"
        );
        RetrieveHit h1 = new RetrieveHit("a", "orion", 0.9, null, List.of(), null, "p1", meta1);
        RetrieveHit h2 = new RetrieveHit("b", "orion", 0.8, null, List.of(), null, "p2", meta2);

        assertThat(TechNeededRecovery.fromSemanticHits(List.of(h1, h2)))
            .containsExactly("SSRF mitigation", "Node.js", "Kafka");
    }
}
