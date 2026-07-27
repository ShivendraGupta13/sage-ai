package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

public record RetrieveMetadata(
    String title,
    String teamName,
    String teamId,
    List<PersonMetadata> people,
    List<String> technologies,
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    List<String> documentLink,
    String category,
    String sourceAttribution
) {
    public RetrieveMetadata {
        if (teamName == null) teamName = "N/A";
        if (teamId == null) teamId = "0";
        if (people == null) people = List.of();
        if (technologies == null) technologies = List.of();
        if (documentLink == null) documentLink = List.of();
        if (category == null) category = "HARD_PROBLEMS";
        if (sourceAttribution == null) sourceAttribution = "Orion API";
    }

    public String getFirstDocumentLink() {
        return (documentLink != null && !documentLink.isEmpty()) ? documentLink.get(0) : null;
    }
}
