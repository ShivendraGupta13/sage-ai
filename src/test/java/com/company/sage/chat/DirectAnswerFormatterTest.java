package com.company.sage.chat;

import com.company.sage.model.CardResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectAnswerFormatterTest {

    @Test
    void shouldReturnNoPriorArtWhenResultsEmpty() {
        assertThat(DirectAnswerFormatter.format(List.of()))
            .isEqualTo("No internal prior art found.");
        assertThat(DirectAnswerFormatter.format(null))
            .isEqualTo("No internal prior art found.");
    }

    @Test
    void shouldFormatSingleTeamRouteFirstOneLiner() {
        CardResult top = new CardResult(
            1, 0.9, List.of("semantic"), "Payments Platform",
            "SSRF-safe external image loader", "HARD_PROBLEMS",
            List.of("Priya Sharma"), "Implemented a proxy...", "https://ticket/1",
            "evidence", List.of("Orion API")
        );

        assertThat(DirectAnswerFormatter.format(List.of(top)))
            .isEqualTo("Yes — 1 team has solved this: Payments Platform (SSRF-safe external image loader).");
    }

    @Test
    void shouldCountDistinctTeamsInOneLiner() {
        CardResult a = new CardResult(
            1, 0.9, List.of("semantic"), "Payments Platform",
            "SSRF-safe external image loader", "HARD_PROBLEMS",
            List.of(), "summary", null, null, List.of()
        );
        CardResult b = new CardResult(
            2, 0.8, List.of("graph"), "Platform Security",
            "SSRF gateway", "HARD_PROBLEMS",
            List.of(), "summary", null, null, List.of()
        );

        assertThat(DirectAnswerFormatter.format(List.of(a, b)))
            .isEqualTo("Yes — 2 teams have solved this: Payments Platform (SSRF-safe external image loader).");
    }
}
