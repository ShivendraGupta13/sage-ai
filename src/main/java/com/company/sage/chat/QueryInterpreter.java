package com.company.sage.chat;

import com.company.sage.adk.agents.QueryInterpretAgent;
import com.company.sage.adk.agents.QueryInterpretationParser;
import com.company.sage.model.QueryInterpretation;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.company.sage.util.OtelSpanHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueryInterpreter {

    private static final Logger log = LoggerFactory.getLogger(QueryInterpreter.class);

    private final BaseLlm adkLlm;

    public QueryInterpreter(BaseLlm adkLlm) {
        this.adkLlm = adkLlm;
    }

    /**
     * Interprets a raw developer query into problemStatement + techNeeded via Ollama.
     * On blank input, LLM failure, or unparsable output: falls back to raw query and empty techNeeded.
     */
    public QueryInterpretation interpret(String rawQuery, String correlationId) {
        OtelSpanHelper.setAttribute("sage.correlation_id", correlationId);
        OtelSpanHelper.setAttribute("rag.query", rawQuery);
        OtelSpanHelper.setAttribute("gen_ai.system", "ollama");
        OtelSpanHelper.setAttribute("gen_ai.request.model", "llama3.2:latest");

        if (rawQuery == null || rawQuery.isBlank()) {
            return new QueryInterpretation("", List.of());
        }

        try {
            LlmRequest request = LlmRequest.builder()
                .appendInstructions(List.of(QueryInterpretAgent.INSTRUCTION))
                .contents(List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText(rawQuery)))
                        .build()
                ))
                .build();

            LlmResponse response = adkLlm.generateContent(request, false).blockingFirst();
            String rawOutput = response.content()
                .map(Content::text)
                .orElse(null);

            return QueryInterpretationParser.parse(rawOutput, rawQuery);
        } catch (Exception e) {
            log.warn(
                "QueryInterpret failed for correlationId {}: {}. Falling back to raw query.",
                correlationId,
                e.getMessage()
            );
            return new QueryInterpretation(rawQuery, List.of());
        }
    }
}
