package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RetrieveResponse(
    List<RetrieveHit> hits,
    @JsonProperty("query_time_ms") Long queryTimeMs,
    @JsonProperty("total_found") Integer totalFound
) {
    public RetrieveResponse {
        if (hits == null) hits = List.of();
    }
}
