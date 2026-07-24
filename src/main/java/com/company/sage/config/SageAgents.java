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
                .build();
    }

    @Bean
    public ParallelAgent parallelRetrieve(LlmAgent semanticSearchAgent, LlmAgent graphTraversalAgent) {
        return ParallelAgent.builder()
                .name("ParallelRetrieve")
                .subAgents(semanticSearchAgent, graphTraversalAgent)
                .build();
    }

    @Bean
    public LlmAgent merger(BaseLlm adkModel, com.company.sage.adk.tools.ResultMergerTool mergerTool) {
        return LlmAgent.builder()
                .name("ResultMerger")
                .model(adkModel)
                .tools(List.of(FunctionTool.create(mergerTool, "merge")))
                .instruction(
                        "Call merge with no arguments. It reads semantic_hits and graph_hits from session state "
                                + "and writes merged_hits. Acknowledge when done.")
                .build();
    }

    @Bean
    public LlmAgent synth(BaseLlm adkModel) {
        return LlmAgent.builder()
                .name("KnowledgeCardSynth")
                .model(adkModel)
                .instruction("""
                    Assemble a Knowledge Card from merged_hits and query_interpretation.
                    - query: echo the original user question
                    - problemStatement / techNeeded: from query_interpretation
                    - directAnswer: one sentence derived from the top result
                    - results[]: from merged_hits — map all fields directly; do NOT paraphrase summary/passage
                    - gapFlag: true if merged_hits is empty
                    - gapMessage: "No internal prior art found — this may be a candidate Hard Problem" when gapFlag=true
                    - Never invent teams, people, or documents
                    - Emit strict JSON matching the Knowledge Card schema in architecture.md §11
                    """)
                .outputKey("knowledge_card")
                .build();
    }

    @Bean
    public com.google.adk.agents.SequentialAgent sageRootAgent(
            LlmAgent queryInterpret,
            ParallelAgent parallelRetrieve,
            LlmAgent merger,
            LlmAgent synth) {

        return com.google.adk.agents.SequentialAgent.builder()
                .name("SageRoot")
                .subAgents(queryInterpret, parallelRetrieve, merger, synth)
                .build();
    }
}
