package com.company.sage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for retrieval.
 */
@ConfigurationProperties(prefix = "sage.retrieval")
public class RetrievalProperties {

    private int topK = 5;
    private double minScore = 0.60;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }
}
