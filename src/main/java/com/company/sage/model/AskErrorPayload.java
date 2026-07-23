package com.company.sage.model;

/**
 * Payload for the error events emitted in the SSE stream.
 */
public class AskErrorPayload {

    private String message;
    private String detail;
    private String code;
    private String correlationId;

    public AskErrorPayload() {
    }

    public AskErrorPayload(String message, String detail, String code, String correlationId) {
        this.message = message;
        this.detail = detail;
        this.code = code;
        this.correlationId = correlationId;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
