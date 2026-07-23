package com.company.sage.model;

import java.util.List;

/**
 * Payload for the status events emitted in the SSE stream.
 */
public class AskStatusPayload {

    private String message;
    private String problemStatement;
    private List<String> techNeeded;

    public AskStatusPayload() {
    }

    public AskStatusPayload(String message) {
        this.message = message;
    }

    public AskStatusPayload(String message, String problemStatement, List<String> techNeeded) {
        this.message = message;
        this.problemStatement = problemStatement;
        this.techNeeded = techNeeded;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getProblemStatement() {
        return problemStatement;
    }

    public void setProblemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
    }

    public List<String> getTechNeeded() {
        return techNeeded;
    }

    public void setTechNeeded(List<String> techNeeded) {
        this.techNeeded = techNeeded;
    }
}
