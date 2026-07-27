package com.company.sage.model;

public record FieldErrorDetail(
    String field,
    String reason,
    String rejectedValue
) {}
