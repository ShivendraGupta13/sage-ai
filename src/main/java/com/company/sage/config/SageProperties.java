package com.company.sage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sage")
public record SageProperties(
    GraphRag graphRag,
    Adk adk,
    Retrieval retrieval,
    Scoring scoring
) {
    public record GraphRag(String baseUrl) {}

    public record Adk(Llm llm) {
        public record Llm(String baseUrl, String modelName) {}
    }

    public record Retrieval(int topK, double minScore) {}

    public record Scoring(double w1, double w2, double dualMatchBoost, double minScore) {}
}
