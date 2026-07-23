package com.company.sage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for result scoring.
 */
@ConfigurationProperties(prefix = "sage.scoring")
public class ScoringProperties {

    private double w1 = 0.6;
    private double w2 = 0.4;
    private double dualMatchBoost = 0.1;
    private double minScore = 0.60;

    public double getW1() {
        return w1;
    }

    public void setW1(double w1) {
        this.w1 = w1;
    }

    public double getW2() {
        return w2;
    }

    public void setW2(double w2) {
        this.w2 = w2;
    }

    public double getDualMatchBoost() {
        return dualMatchBoost;
    }

    public void setDualMatchBoost(double dualMatchBoost) {
        this.dualMatchBoost = dualMatchBoost;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }
}
