package com.company.sage.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Knowledge Card representing the structured result of the ask query orchestration.
 */
public class KnowledgeCard {

    private String query;
    private String problemStatement;
    private List<String> techNeeded;
    private String directAnswer;
    private List<KnowledgeCardResult> results = new ArrayList<>();
    private boolean gapFlag;
    private String gapMessage;

    public KnowledgeCard() {
    }

    public KnowledgeCard(
            String query,
            String problemStatement,
            List<String> techNeeded,
            String directAnswer,
            List<KnowledgeCardResult> results,
            boolean gapFlag,
            String gapMessage) {
        this.query = query;
        this.problemStatement = problemStatement;
        this.techNeeded = techNeeded;
        this.directAnswer = directAnswer;
        if (results != null) {
            this.results = results;
        }
        this.gapFlag = gapFlag;
        this.gapMessage = gapMessage;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getProblemStatement() {
        return problemStatement;
    }

    public void setProblemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
    }

    public List<String> getTechNeeded() {
        return techNeeded;
    }

    public void setTechNeeded(List<String> techNeeded) {
        this.techNeeded = techNeeded;
    }

    public String getDirectAnswer() {
        return directAnswer;
    }

    public void setDirectAnswer(String directAnswer) {
        this.directAnswer = directAnswer;
    }

    public List<KnowledgeCardResult> getResults() {
        return results;
    }

    public void setResults(List<KnowledgeCardResult> results) {
        if (results != null) {
            this.results = results;
        } else {
            this.results = new ArrayList<>();
        }
    }

    public boolean isGapFlag() {
        return gapFlag;
    }

    public void setGapFlag(boolean gapFlag) {
        this.gapFlag = gapFlag;
    }

    public String getGapMessage() {
        return gapMessage;
    }

    public void setGapMessage(String gapMessage) {
        this.gapMessage = gapMessage;
    }
}
