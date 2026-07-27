package com.company.sage.adk;

import com.company.sage.adk.agents.GraphTraversalAgent;
import com.company.sage.adk.agents.KnowledgeCardSynthAgent;
import com.company.sage.adk.agents.ParallelRetrieveAgent;
import com.company.sage.adk.agents.QueryInterpretAgent;
import com.company.sage.adk.agents.SemanticSearchAgent;
import com.company.sage.adk.tools.GraphTraversalTool;
import com.company.sage.adk.tools.ResultMergerTool;
import com.company.sage.adk.tools.SemanticSearchTool;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SageAgents {

    @Bean
    public SequentialAgent sageRootAgent(
        BaseLlm adkLlm,
        SemanticSearchTool semanticTool,
        GraphTraversalTool graphTool,
        ResultMergerTool mergerTool
    ) {
        LlmAgent queryInterpret = QueryInterpretAgent.create(adkLlm);

        LlmAgent semanticSearch = SemanticSearchAgent.create(adkLlm, semanticTool);
        LlmAgent graphTraversal = GraphTraversalAgent.create(adkLlm, graphTool);

        ParallelAgent parallelRetrieve = ParallelRetrieveAgent.create(semanticSearch, graphTraversal);

        LlmAgent merger = LlmAgent.builder()
            .name("ResultMerger")
            .model(adkLlm)
            .tools(List.of(FunctionTool.create(mergerTool, "merge")))
            .instruction("Call merge with no arguments. It reads semantic_hits and graph_hits from session state and writes merged_hits.")
            .build();

        LlmAgent synth = KnowledgeCardSynthAgent.create(adkLlm);

        return SequentialAgent.builder()
            .name("SageRoot")
            .subAgents(List.of(queryInterpret, parallelRetrieve, merger, synth))
            .build();
    }
}
