package com.company.sage.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared HTTP Error Response model (RFC 7807 representation).
 */
public class ErrorResponse {

    private String type = "about:blank";
    private String title;
    private int status;
    private String code;
    private String message;
    private String detail;
    private String instance;
    private String correlationId = "unknown";
    private List<ValidationError> errors = new ArrayList<>();

    public ErrorResponse() {
    }

    public ErrorResponse(
            String type,
            String title,
            int status,
            String code,
            String message,
            String detail,
            String instance,
            String correlationId,
            List<ValidationError> errors) {
        if (type != null) {
            this.type = type;
        }
        this.title = title;
        this.status = status;
        this.code = code;
        this.message = message;
        this.detail = detail;
        this.instance = instance;
        if (correlationId != null) {
            this.correlationId = correlationId;
        }
        if (errors != null) {
            this.errors = errors;
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public void setErrors(List<ValidationError> errors) {
        if (errors != null) {
            this.errors = errors;
        } else {
            this.errors = new ArrayList<>();
        }
    }
}
