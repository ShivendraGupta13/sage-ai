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
          "problemStatement": "<one sentence: what technical problem is being solved in organisational context>",
          "techNeeded": ["<technology, protocol, or architectural concept 1>", "..."]
        }
        Strict Extraction Rules:
        - problemStatement must paraphrase the user question as a clear engineering problem. Do NOT copy the question verbatim.
        - techNeeded MUST contain ONLY real technologies, frameworks, protocols, databases, languages, or architectural patterns (e.g., "Keycloak", "OAuth2", "SSRF", "Node.js", "Kafka", "Prometheus", "AWS").
        - NEVER include non-technical conversational English words in techNeeded (e.g., do NOT include "safely", "fetching", "external", "images", "emails", "without", "exposure", "implementation", "using", "common", "how", "solve").
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
