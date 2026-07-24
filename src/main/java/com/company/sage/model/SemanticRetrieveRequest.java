package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for semantic retrieve call to downstream Graph RAG service.
 */
public class SemanticRetrieveRequest {

    private String problemStatement;

    @JsonProperty("top_k")
    private int topK = 5;

    @JsonProperty("min_score")
    private double minScore = 0.60;

    /** When false, Graph RAG skips its own LLM path (Sage owns interpretation/synthesis). */
    @JsonProperty("use_llm")
    private boolean useLlm = false;

    public SemanticRetrieveRequest() {
    }

    public SemanticRetrieveRequest(String problemStatement, int topK, double minScore) {
        this.problemStatement = problemStatement;
        this.topK = topK;
        this.minScore = minScore;
        this.useLlm = false;
    }

    public String getProblemStatement() {
        return problemStatement;
    }

    public void setProblemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
    }

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

    public boolean isUseLlm() {
        return useLlm;
    }

    public void setUseLlm(boolean useLlm) {
        this.useLlm = useLlm;
    }
}
