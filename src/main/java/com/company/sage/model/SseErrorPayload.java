package com.company.sage.model;

public record SseErrorPayload(
    String message,
    String detail,
    String code,
    String correlationId
) {}
