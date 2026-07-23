package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for the /ask endpoint.
 */
public class AskRequest {

    private String query;

    public AskRequest() {
    }

    @JsonCreator
    public AskRequest(@JsonProperty("query") String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
