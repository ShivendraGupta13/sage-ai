package com.company.sage.model;

public record HealthResponse(
    String status,
    boolean semanticServiceReachable,
    boolean graphServiceReachable
) {}
