package com.company.sage.adk.agents;

import com.company.sage.adk.tools.SemanticSearchTool;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;

import java.util.List;

public final class SemanticSearchAgent {

    public static final String AGENT_NAME = "SemanticSearchAgent";
    public static final String INSTRUCTION = """
        Call semanticSearch with the problemStatement extracted during query interpretation.
        Report only tool results. Never invent teams, people, or documents.
        """;

    private SemanticSearchAgent() {}

    public static LlmAgent create(BaseLlm model, SemanticSearchTool semanticTool) {
        FunctionTool tool = FunctionTool.create(semanticTool, "semanticSearch");
        return LlmAgent.builder()
            .name(AGENT_NAME)
            .model(model)
            .tools(List.of(tool))
            .instruction(INSTRUCTION)
            .build();
    }
}
