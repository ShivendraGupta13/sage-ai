package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request DTO for graph retrieve call to downstream Graph RAG service.
 */
public class GraphRetrieveRequest {

    private List<String> techNeeded;

    @JsonProperty("top_k")
    private int topK = 5;

    public GraphRetrieveRequest() {
    }

    public GraphRetrieveRequest(List<String> techNeeded, int topK) {
        this.techNeeded = techNeeded;
        this.topK = topK;
    }

    public List<String> getTechNeeded() {
        return techNeeded;
    }

    public void setTechNeeded(List<String> techNeeded) {
        this.techNeeded = techNeeded;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }
}
