package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Validation error details for field validations.
 */
public class ValidationError {

    private String field;
    private String reason;
    private String rejectedValue;

    public ValidationError() {
    }

    @JsonCreator
    public ValidationError(
            @JsonProperty("field") String field,
            @JsonProperty("reason") String reason,
            @JsonProperty("rejectedValue") String rejectedValue) {
        this.field = field;
        this.reason = reason;
        this.rejectedValue = rejectedValue;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRejectedValue() {
        return rejectedValue;
    }

    public void setRejectedValue(String rejectedValue) {
        this.rejectedValue = rejectedValue;
    }
}
