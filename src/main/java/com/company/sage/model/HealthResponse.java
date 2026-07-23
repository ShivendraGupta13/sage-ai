package com.company.sage.model;

/**
 * Health response model for the public /health endpoint.
 */
public class HealthResponse {

    private String status = "ok";
    private boolean semanticServiceReachable;
    private boolean graphServiceReachable;

    public HealthResponse() {
    }

    public HealthResponse(String status, boolean semanticServiceReachable, boolean graphServiceReachable) {
        this.status = status;
        this.semanticServiceReachable = semanticServiceReachable;
        this.graphServiceReachable = graphServiceReachable;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSemanticServiceReachable() {
        return semanticServiceReachable;
    }

    public void setSemanticServiceReachable(boolean semanticServiceReachable) {
        this.semanticServiceReachable = semanticServiceReachable;
    }

    public boolean isGraphServiceReachable() {
        return graphServiceReachable;
    }

    public void setGraphServiceReachable(boolean graphServiceReachable) {
        this.graphServiceReachable = graphServiceReachable;
    }
}
