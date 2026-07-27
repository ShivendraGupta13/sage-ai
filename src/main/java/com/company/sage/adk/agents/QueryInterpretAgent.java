package com.company.sage.adk.agents;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;

public final class QueryInterpretAgent {

    public static final String AGENT_NAME = "QueryInterpret";
    public static final String OUTPUT_KEY = "query_interpretation";

    public static final String INSTRUCTION = """
        You are interpreting a developer's question about prior art in our organisation.
        Extract exactly two things and output strict JSON matching this structure:
        {
          "problemStatement": "<one sentence: what problem is being solved in organisational context>",
          "techNeeded": ["<technology or pattern 1>", "..."]
        }
        Rules:
        - problemStatement must paraphrase the question as an organisational problem. Do NOT copy the question verbatim.
        - techNeeded must list real technologies, tools, or patterns. Do NOT put generic English words from the question (e.g. "prevent", "how", "data", "loss").
        Do not add Markdown formatting, code blocks, or extra text outside the JSON. Output ONLY the JSON object.
        """;

    private QueryInterpretAgent() {}

    public static LlmAgent create(BaseLlm model) {
        return LlmAgent.builder()
            .name(AGENT_NAME)
            .model(model)
            .instruction(INSTRUCTION)
            .outputKey(OUTPUT_KEY)
            .build();
    }
}
