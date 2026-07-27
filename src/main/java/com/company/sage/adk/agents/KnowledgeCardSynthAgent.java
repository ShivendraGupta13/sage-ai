package com.company.sage.adk.agents;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;

public final class KnowledgeCardSynthAgent {

    public static final String AGENT_NAME = "KnowledgeCardSynth";
    public static final String OUTPUT_KEY = "knowledge_card";

    public static final String INSTRUCTION = """
        Assemble a Knowledge Card from merged_hits and query_interpretation in session state.
        Output strict JSON matching this exact structure:
        {
          "query": "<user question>",
          "problemStatement": "<from query_interpretation or default>",
          "techNeeded": ["<from query_interpretation>"],
          "directAnswer": "<1-2 sentence direct solution answering who solved this problem and how based strictly on top merged_hits evidence, or 'No internal prior art found.' if results is empty>",
          "results": [ ... map all fields directly from merged_hits ... ],
          "gapFlag": <true if merged_hits is empty, otherwise false>,
          "gapMessage": "<'No internal prior art found — this may be a candidate Hard Problem' if gapFlag is true, otherwise null>"
        }
        Strict Rules:
        - directAnswer must synthesize a 1-2 sentence solution referencing who solved it and how using top result evidence.
        - Never invent teams, people, or documents not present in the retrieved evidence.
        - Output ONLY strict JSON. Do not include markdown code blocks or extra conversational text outside JSON.
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
