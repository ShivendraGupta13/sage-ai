package com.company.sage.adk.agents;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;

public final class KnowledgeCardSynthAgent {

    public static final String AGENT_NAME = "KnowledgeCardSynth";
    public static final String OUTPUT_KEY = "knowledge_card";

    public static final String INSTRUCTION = """
        Assemble a Knowledge Card from merged_hits and query_interpretation in session state.
        Output strict JSON matching this structure:
        {
          "query": "<user question>",
          "problemStatement": "<from query_interpretation or default>",
          "techNeeded": ["<from query_interpretation>"],
          "directAnswer": "<one sentence answer summarizing top result, or 'No internal prior art found.' if results is empty>",
          "results": [ ... map all fields directly from merged_hits ... ],
          "gapFlag": <true if merged_hits is empty, otherwise false>,
          "gapMessage": "<'No internal prior art found — this may be a candidate Hard Problem' if gapFlag is true, otherwise null>"
        }
        Never invent teams, people, or documents. Output ONLY strict JSON.
        """;

    private KnowledgeCardSynthAgent() {}

    public static LlmAgent create(BaseLlm model) {
        return LlmAgent.builder()
            .name(AGENT_NAME)
            .model(model)
            .instruction(INSTRUCTION)
            .outputKey(OUTPUT_KEY)
            .build();
    }
}
