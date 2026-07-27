package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RetrieveHit(
    @JsonProperty("doc_id") String docId,
    String source,
    Double vectorScore,
    Double graphScore,
    List<String> matchedTags,
    String pathDescription,
    String passage,
    RetrieveMetadata metadata
) {
    public RetrieveHit {
        if (matchedTags == null) matchedTags = List.of();
    }
}
