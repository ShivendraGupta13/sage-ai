package com.company.sage.eval;

import com.company.sage.model.CardResult;
import com.company.sage.util.OtelSpanHelper;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);
    private final BaseLlm adkLlm;

    public JudgeService(BaseLlm adkLlm) {
        this.adkLlm = adkLlm;
    }

    /**
     * Asynchronously evaluates the response quality (Faithfulness & Relevance) 
     * using the local LLM as a judge without blocking the main SSE response thread.
     */
    public CompletableFuture<Void> evaluateAsync(
        String rawQuery,
        String directAnswer,
        List<CardResult> contextHits,
        String correlationId,
        io.opentelemetry.api.trace.Span parentSpan
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (directAnswer == null || directAnswer.isBlank()) {
                    if (parentSpan != null) {
                        parentSpan.setAttribute("eval.faithfulness", "N/A");
                        parentSpan.setAttribute("eval.relevance", "N/A");
                    }
                    return;
                }

                String contextText = (contextHits != null && !contextHits.isEmpty())
                    ? contextHits.stream()
                        .map(c -> String.format("[%s] %s", c.hardProblemTitle(), c.summary()))
                        .collect(Collectors.joining("\n"))
                    : "No context retrieved.";

                String judgePrompt = String.format("""
                    You are an expert AI evaluator for a RAG (Retrieval-Augmented Generation) system.
                    Evaluate the AI answer strictly and return a JSON object with exactly these 5 keys.

                    USER QUERY: %s
                    RETRIEVED CONTEXT: %s
                    AI ANSWER: %s

                    Evaluate on these 5 dimensions:
                    1. faithfulness (1-5): Is the answer grounded in retrieved context with no hallucinations?
                    2. relevance (1-5): Does the answer directly address the user query?
                    3. context_precision (1-5): Were the retrieved chunks useful/relevant, or mostly noise?
                    4. hallucination (0 or 1): Does the answer contain ANY claim not supported by the context? 0=no, 1=yes
                    5. completeness (1-5): Did the answer fully address all parts of the question?
                    
                    Also provide a brief 'reasoning' for your scores.

                    Return ONLY valid JSON, no explanation outside the JSON:
                    {"faithfulness": 4, "relevance": 4, "context_precision": 3, "hallucination": 0, "completeness": 4, "reasoning": "brief explanation"}
                    """, rawQuery, contextText, directAnswer);

                LlmRequest request = LlmRequest.builder()
                    .contents(List.of(
                        Content.builder()
                            .role("user")
                            .parts(List.of(Part.fromText(judgePrompt)))
                            .build()
                    ))
                    .build();

                LlmResponse response = adkLlm.generateContent(request, false).blockingFirst();
                String resultText = response.content()
                    .map(Content::text)
                    .orElse("");

                // Parse all 5 scores (fail-soft — defaults if parsing fails)
                int faithfulness     = extractScore(resultText, "faithfulness");
                int relevance        = extractScore(resultText, "relevance");
                int contextPrecision = extractScore(resultText, "context_precision");
                int hallucination    = extractScore(resultText, "hallucination");
                int completeness     = extractScore(resultText, "completeness");
                String reasoning     = extractString(resultText, "reasoning");

                if (parentSpan != null) {
                    parentSpan.setAttribute("eval.faithfulness",      faithfulness     > 0 ? String.valueOf(faithfulness)     : "3");
                    parentSpan.setAttribute("eval.relevance",         relevance        > 0 ? String.valueOf(relevance)        : "3");
                    parentSpan.setAttribute("eval.context_precision", contextPrecision > 0 ? String.valueOf(contextPrecision) : "3");
                    parentSpan.setAttribute("eval.hallucination",     String.valueOf(hallucination)); // 0 or 1, keep raw
                    parentSpan.setAttribute("eval.completeness",      completeness     > 0 ? String.valueOf(completeness)     : "3");
                    if (reasoning != null && !reasoning.isEmpty()) {
                        parentSpan.setAttribute("eval.reasoning", reasoning);
                    }
                    parentSpan.setAttribute("eval.judge_raw",         resultText);
                }

                log.info("LLM-as-a-Judge completed for correlationId {}: Faithfulness={}, Relevance={}, ContextPrecision={}, Hallucination={}, Completeness={}",
                    correlationId,
                    faithfulness     > 0 ? faithfulness     : 3,
                    relevance        > 0 ? relevance        : 3,
                    contextPrecision > 0 ? contextPrecision : 3,
                    hallucination,
                    completeness     > 0 ? completeness     : 3);

            } catch (Exception e) {
                log.warn("Fail-soft: LLM-as-a-Judge async evaluation failed for correlationId {}: {}", correlationId, e.getMessage());
                if (parentSpan != null) {
                    parentSpan.setAttribute("eval.faithfulness",      "3");
                    parentSpan.setAttribute("eval.relevance",         "3");
                    parentSpan.setAttribute("eval.context_precision", "3");
                    parentSpan.setAttribute("eval.hallucination",     "0");
                    parentSpan.setAttribute("eval.completeness",      "3");
                }
            }
        });
    }

    private int extractScore(String jsonText, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
            java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = r.matcher(jsonText);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private String extractString(String jsonText, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = r.matcher(jsonText);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
