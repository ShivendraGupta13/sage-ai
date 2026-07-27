package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GraphRetrieveRequest(
    List<String> techNeeded,
    @JsonProperty("top_k") int topK
) {
    public GraphRetrieveRequest {
        if (techNeeded == null) techNeeded = List.of();
    }
}
