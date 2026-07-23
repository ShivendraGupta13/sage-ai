package com.company.sage.model;

import java.util.List;

/**
 * Model representing a merged search result with calculated confidence score.
 */
public class MergedHit {

    private String docId;
    private int rank;
    private double confidenceScore;
    private List<String> matchedVia;
    private String teamName;
    private String hardProblemTitle;
    private String category;
    private List<String> solvedBy;
    private String summary;
    private String documentLink;
    private String evidenceDetail;
    private List<String> sourceAttribution;

    public MergedHit() {
    }

    public MergedHit(
            String docId,
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
            List<String> sourceAttribution) {
        this.docId = docId;
        this.rank = rank;
        this.confidenceScore = confidenceScore;
        this.matchedVia = matchedVia;
        this.teamName = teamName;
        this.hardProblemTitle = hardProblemTitle;
        this.category = category;
        this.solvedBy = solvedBy;
        this.summary = summary;
        this.documentLink = documentLink;
        this.evidenceDetail = evidenceDetail;
        this.sourceAttribution = sourceAttribution;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public List<String> getMatchedVia() {
        return matchedVia;
    }

    public void setMatchedVia(List<String> matchedVia) {
        this.matchedVia = matchedVia;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getHardProblemTitle() {
        return hardProblemTitle;
    }

    public void setHardProblemTitle(String hardProblemTitle) {
        this.hardProblemTitle = hardProblemTitle;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getSolvedBy() {
        return solvedBy;
    }

    public void setSolvedBy(List<String> solvedBy) {
        this.solvedBy = solvedBy;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDocumentLink() {
        return documentLink;
    }

    public void setDocumentLink(String documentLink) {
        this.documentLink = documentLink;
    }

    public String getEvidenceDetail() {
        return evidenceDetail;
    }

    public void setEvidenceDetail(String evidenceDetail) {
        this.evidenceDetail = evidenceDetail;
    }

    public List<String> getSourceAttribution() {
        return sourceAttribution;
    }

    public void setSourceAttribution(List<String> sourceAttribution) {
        this.sourceAttribution = sourceAttribution;
    }
}
