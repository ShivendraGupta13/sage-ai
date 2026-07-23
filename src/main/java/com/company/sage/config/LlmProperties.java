package com.company.sage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the ADK LLM chat model.
 */
@ConfigurationProperties(prefix = "sage.adk.llm")
public class LlmProperties {

    private String baseUrl = "http://localhost:11434";
    private String modelName = "llama3.2:3b";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}
