package com.company.sage.model;

import java.util.List;

public record CardResult(
    int rank,
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
) {
    public CardResult {
        if (matchedVia == null) matchedVia = List.of();
        if (solvedBy == null) solvedBy = List.of();
        if (sourceAttribution == null) sourceAttribution = List.of();
    }
}
