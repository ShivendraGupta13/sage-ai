package com.company.sage.model;

import java.util.List;

public record ApiErrorResponse(
    String type,
    String title,
    int status,
    String code,
    String message,
    String detail,
    String instance,
    String correlationId,
    List<FieldErrorDetail> errors
) {
    public ApiErrorResponse {
        if (type == null) type = "about:blank";
        if (errors == null) errors = List.of();
    }
}
