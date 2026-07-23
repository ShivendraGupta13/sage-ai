package com.company.sage.config;

import com.company.sage.adk.tools.GraphTraversalTool;
import com.company.sage.adk.tools.SemanticSearchTool;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class that registers the ADK agents as beans.
 */
@Configuration
public class SageAgents {

    @Bean
    public LlmAgent queryInterpret(BaseLlm adkModel) {
        return LlmAgent.builder()
                .name("QueryInterpret")
                .model(adkModel)
                .instruction("""
                    You are interpreting a developer's question about prior art in our organisation.
                    Extract exactly two things and output strict JSON:
                    {
                      "problemStatement": "<one sentence: what problem is being solved in organisational context>",
                      "techNeeded": ["<technology or pattern>", "..."]
                    }
                    Do not add explanation. Output only the JSON object.
                    """)
                .outputKey("query_interpretation")
                .build();
    }

    @Bean
    public LlmAgent semanticSearchAgent(BaseLlm adkModel, SemanticSearchTool semanticTool) {
        return LlmAgent.builder()
                .name("SemanticSearchAgent")
                .model(adkModel)
                .tools(List.of(FunctionTool.create(semanticTool, "semanticSearch")))
                .instruction("""
                    Call semanticSearch with the problemStatement from query_interpretation.
                    Report only tool results. Never invent teams, people, or documents.
                    """)
                .outputKey("semantic_hits")
                .build();
    }

    @Bean
    public LlmAgent graphTraversalAgent(BaseLlm adkModel, GraphTraversalTool graphTool) {
        return LlmAgent.builder()
                .name("GraphTraversalAgent")
                .model(adkModel)
                .tools(List.of(FunctionTool.create(graphTool, "graphTraversal")))
                .instruction("""
                    Call graphTraversal with the techNeeded array from query_interpretation.
                    Report only tool results. Never invent teams, people, or documents.
                    """)
                .outputKey("graph_hits")
                .build();
    }

    @Bean
    public ParallelAgent parallelRetrieve(LlmAgent semanticSearchAgent, LlmAgent graphTraversalAgent) {
        return ParallelAgent.builder()
                .name("ParallelRetrieve")
                .subAgents(semanticSearchAgent, graphTraversalAgent)
                .build();
    }
}
