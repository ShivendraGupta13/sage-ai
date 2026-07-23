package com.company.sage.merge;

/**
 * Configuration parameters for the ResultMerger scoring.
 */
public class ScoringConfig {

    private final double w1;
    private final double w2;
    private final double dualMatchBoost;
    private final double minScore;
    private final int topK;

    public ScoringConfig(double w1, double w2, double dualMatchBoost, double minScore, int topK) {
        this.w1 = w1;
        this.w2 = w2;
        this.dualMatchBoost = dualMatchBoost;
        this.minScore = minScore;
        this.topK = topK;
    }

    public double getW1() {
        return w1;
    }

    public double getW2() {
        return w2;
    }

    public double getDualMatchBoost() {
        return dualMatchBoost;
    }

    public double getMinScore() {
        return minScore;
    }

    public int getTopK() {
        return topK;
    }
}
