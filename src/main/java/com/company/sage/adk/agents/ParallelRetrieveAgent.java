package com.company.sage.adk.agents;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;

import java.util.List;

public final class ParallelRetrieveAgent {

    public static final String AGENT_NAME = "ParallelRetrieve";

    private ParallelRetrieveAgent() {}

    public static ParallelAgent create(LlmAgent semanticSearchAgent, LlmAgent graphTraversalAgent) {
        return ParallelAgent.builder()
            .name(AGENT_NAME)
            .subAgents(List.of(semanticSearchAgent, graphTraversalAgent))
            .build();
    }
}
