package com.company.sage.adk.agents;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;

public final class QueryInterpretAgent {

    public static final String AGENT_NAME = "QueryInterpret";
    public static final String OUTPUT_KEY = "query_interpretation";

    public static final String INSTRUCTION = """
        You are a Query Interpretation Agent for an internal engineering knowledge base.
        The user may send any form of input: a full question, a keyword phrase, a single technology name, or a raw problem fragment.
        Regardless of the format, understand the user's intent and return ONLY this JSON — no markdown, no extra text:

        {
          "problemStatement": "<2-3 sentences: the engineering problem being addressed, the technical goal, and any implied constraints or failure modes>",
          "techNeeded": ["<tech explicitly in query>", "<well-known ecosystem peer or alternative>", "..."]
        }

        problemStatement: Infer the engineering context from the query, however raw it is. Rewrite it as a precise engineering problem. Preserve all tech names. State the technical goal. Include constraints only if clearly implied. Do NOT invent details not in the query. If the query signals interest in past or prior work (e.g. phrased as "how did we", "what did we use", "how have we", or any query directed at an internal knowledge base), the final sentence must state: the goal is to find prior work, implementations, or decisions made within the organization related to this problem.
        techNeeded: Include technologies named in the query AND their well-known ecosystem alternatives (e.g. Kafka → Pulsar, Kinesis; Elasticsearch → OpenSearch, Solr; Redis → Memcached; Kubernetes → ECS, Nomad). Use only real tech terms — no English words, verbs, or adjectives.
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
