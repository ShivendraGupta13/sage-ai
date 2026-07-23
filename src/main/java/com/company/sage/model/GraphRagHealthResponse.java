package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Health response model returned by the downstream Graph RAG service.
 */
public class GraphRagHealthResponse {

    private String status;

    @JsonProperty("last_seed_run")
    private String lastSeedRun;

    @JsonProperty("indexed_records")
    private int indexedRecords;

    @JsonProperty("neo4j_reachable")
    private boolean neo4jReachable;

    public GraphRagHealthResponse() {
    }

    public GraphRagHealthResponse(String status, String lastSeedRun, int indexedRecords, boolean neo4jReachable) {
        this.status = status;
        this.lastSeedRun = lastSeedRun;
        this.indexedRecords = indexedRecords;
        this.neo4jReachable = neo4jReachable;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastSeedRun() {
        return lastSeedRun;
    }

    public void setLastSeedRun(String lastSeedRun) {
        this.lastSeedRun = lastSeedRun;
    }

    public int getIndexedRecords() {
        return indexedRecords;
    }

    public void setIndexedRecords(int indexedRecords) {
        this.indexedRecords = indexedRecords;
    }

    public boolean isNeo4jReachable() {
        return neo4jReachable;
    }

    public void setNeo4jReachable(boolean neo4jReachable) {
        this.neo4jReachable = neo4jReachable;
    }
}
