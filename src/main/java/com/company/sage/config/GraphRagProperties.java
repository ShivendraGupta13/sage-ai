package com.company.sage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Graph RAG retrieval service.
 */
@ConfigurationProperties(prefix = "sage.graph-rag")
public class GraphRagProperties {

    private String baseUrl = "http://localhost:8000";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
