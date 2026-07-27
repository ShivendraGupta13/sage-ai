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
          "problemStatement": "<one sentence: what problem is being solved>",
          "techNeeded": ["<technology or pattern 1>", "..."]
        }
        techNeeded drives Technology-node graph traversal. Prefer concrete labels (languages,
        frameworks, databases, messaging, cloud, platforms, patterns) — not conversational English.
        Rules:
        - problemStatement must paraphrase the question. Do NOT copy it verbatim. Keep any technology
          named in the question visible in the paraphrase. Do not invent unrelated stack or framing.
        - techNeeded must list ONLY technologies, tools, or patterns explicitly named in the question.
          Prefer an empty array over a guess. Do NOT add companion or "implied" stack
          (e.g. do not add Kibana, .NET, or C# when the question only names Elastic Search).
          Do NOT put generic English words (e.g. "prevent", "how", "data", "loss").
        Examples:
        Q: How do we stream Postgres changes into Kafka without dual writes?
        {"problemStatement":"Capturing PostgreSQL change events into Kafka without dual-write inconsistency","techNeeded":["PostgreSQL","Kafka"]}
        Q: How did we prevent data loss when EC2 spot instances terminate?
        {"problemStatement":"Preserving workload state across EC2 spot instance interruptions","techNeeded":["EC2"]}
        Q: How to use Elastic Search.
        {"problemStatement":"Using Elasticsearch for search and retrieval","techNeeded":["Elasticsearch"]}
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
