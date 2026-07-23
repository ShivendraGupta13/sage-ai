package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO returned by Graph RAG retrieval endpoints.
 */
public class RetrieveResponse {

    private List<RetrieveHit> hits = new ArrayList<>();

    @JsonProperty("query_time_ms")
    private int queryTimeMs;

    @JsonProperty("total_found")
    private int totalFound;

    public RetrieveResponse() {
    }

    public RetrieveResponse(List<RetrieveHit> hits, int queryTimeMs, int totalFound) {
        if (hits != null) {
            this.hits = hits;
        }
        this.queryTimeMs = queryTimeMs;
        this.totalFound = totalFound;
    }

    public List<RetrieveHit> getHits() {
        return hits;
    }

    public void setHits(List<RetrieveHit> hits) {
        if (hits != null) {
            this.hits = hits;
        } else {
            this.hits = new ArrayList<>();
        }
    }

    public int getQueryTimeMs() {
        return queryTimeMs;
    }

    public void setQueryTimeMs(int queryTimeMs) {
        this.queryTimeMs = queryTimeMs;
    }

    public int getTotalFound() {
        return totalFound;
    }

    public void setTotalFound(int totalFound) {
        this.totalFound = totalFound;
    }
}
