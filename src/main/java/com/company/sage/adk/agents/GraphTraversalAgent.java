package com.company.sage.adk.agents;

import com.company.sage.adk.tools.GraphTraversalTool;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;

import java.util.List;

public final class GraphTraversalAgent {

    public static final String AGENT_NAME = "GraphTraversalAgent";
    public static final String INSTRUCTION = """
        Call graphTraversal with the techNeeded array extracted during query interpretation.
        Report only tool results. Never invent teams, people, or documents.
        """;

    private GraphTraversalAgent() {}

    public static LlmAgent create(BaseLlm model, GraphTraversalTool graphTool) {
        FunctionTool tool = FunctionTool.create(graphTool, "graphTraversal");
        return LlmAgent.builder()
            .name(AGENT_NAME)
            .model(model)
            .tools(List.of(tool))
            .instruction(INSTRUCTION)
            .build();
    }
}
