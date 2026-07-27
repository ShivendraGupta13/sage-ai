package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseStatusPayload(
    String message,
    String problemStatement,
    List<String> techNeeded
) {
    public SseStatusPayload(String message) {
        this(message, null, null);
    }
}
