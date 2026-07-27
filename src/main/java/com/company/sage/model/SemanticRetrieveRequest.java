package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SemanticRetrieveRequest(
    String problemStatement,
    @JsonProperty("top_k") int topK,
    @JsonProperty("min_score") double minScore,
    @JsonProperty("use_llm") boolean useLlm
) {
    public SemanticRetrieveRequest(String problemStatement, int topK, double minScore) {
        this(problemStatement, topK, minScore, false);
    }
}
