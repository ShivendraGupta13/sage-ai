package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Model representing a retrieval result hit.
 */
public class RetrieveHit {

    @JsonProperty("doc_id")
    private String docId;

    private String source;

    private Double vectorScore;

    private Double graphScore;

    private List<String> matchedTags;

    private String pathDescription;

    private String passage;

    private RetrieveHitMetadata metadata;

    public RetrieveHit() {
    }

    public RetrieveHit(
            String docId,
            String source,
            Double vectorScore,
            Double graphScore,
            List<String> matchedTags,
            String pathDescription,
            String passage,
            RetrieveHitMetadata metadata) {
        this.docId = docId;
        this.source = source;
        this.vectorScore = vectorScore;
        this.graphScore = graphScore;
        this.matchedTags = matchedTags;
        this.pathDescription = pathDescription;
        this.passage = passage;
        this.metadata = metadata;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Double getVectorScore() {
        return vectorScore;
    }

    public void setVectorScore(Double vectorScore) {
        this.vectorScore = vectorScore;
    }

    public Double getGraphScore() {
        return graphScore;
    }

    public void setGraphScore(Double graphScore) {
        this.graphScore = graphScore;
    }

    public List<String> getMatchedTags() {
        return matchedTags;
    }

    public void setMatchedTags(List<String> matchedTags) {
        this.matchedTags = matchedTags;
    }

    public String getPathDescription() {
        return pathDescription;
    }

    public void setPathDescription(String pathDescription) {
        this.pathDescription = pathDescription;
    }

    public String getPassage() {
        return passage;
    }

    public void setPassage(String passage) {
        this.passage = passage;
    }

    public RetrieveHitMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(RetrieveHitMetadata metadata) {
        this.metadata = metadata;
    }
}
