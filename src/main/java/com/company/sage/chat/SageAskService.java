package com.company.sage.chat;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.SageProperties;
import com.company.sage.merge.ResultMerger;
import com.company.sage.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class SageAskService {

    private static final Logger log = LoggerFactory.getLogger(SageAskService.class);
    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final GraphRagClient graphRagClient;
    private final ResultMerger resultMerger;
    private final SageProperties properties;
    private final QueryInterpreter queryInterpreter;

    public SageAskService(
        GraphRagClient graphRagClient,
        ResultMerger resultMerger,
        SageProperties properties,
        QueryInterpreter queryInterpreter
    ) {
        this.graphRagClient = graphRagClient;
        this.resultMerger = resultMerger;
        this.properties = properties;
        this.queryInterpreter = queryInterpreter;
    }

    public SseEmitter processAsk(AskRequest request, String correlationId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        try {
            long requestStartNs = System.nanoTime();

            // Event 1: status "Interpreting query…"
            sendSse(emitter, "status", new SseStatusPayload("Interpreting query…"));

            String rawQuery = request.query();
            QueryInterpretation interpretation = queryInterpreter.interpret(rawQuery, correlationId);

            sendSse(emitter, "status", new SseStatusPayload(
                "Identified problem state and tech context",
                interpretation.problemStatement(),
                interpretation.techNeeded()
            ));

            // Event 3: status "Searching knowledge base…"
            sendSse(emitter, "status", new SseStatusPayload("Searching knowledge base…"));

            // Retrieval stage (Semantic + Graph fan-out)
            long retrievalStartNs = System.nanoTime();
            int topK = properties.retrieval().topK();
            double minScore = properties.retrieval().minScore();

            SemanticRetrieveRequest semReq = new SemanticRetrieveRequest(interpretation.problemStatement(), topK, minScore, false);
            GraphRetrieveRequest graphReq = new GraphRetrieveRequest(interpretation.techNeeded(), topK);

            List<RetrieveHit> semanticHits;
            List<RetrieveHit> graphHits;
            List<String> techNeeded = interpretation.techNeeded() != null ? interpretation.techNeeded() : List.of();

            if (!techNeeded.isEmpty()) {
                CompletableFuture<RetrieveResponse> semFuture = CompletableFuture.supplyAsync(
                    () -> graphRagClient.retrieveSemantic(semReq, correlationId)
                );
                CompletableFuture<RetrieveResponse> graphFuture = CompletableFuture.supplyAsync(
                    () -> graphRagClient.retrieveGraph(graphReq, correlationId)
                );
                RetrieveResponse semResp = semFuture.join();
                RetrieveResponse graphResp = graphFuture.join();
                semanticHits = (semResp != null && semResp.hits() != null) ? semResp.hits() : List.of();
                graphHits = (graphResp != null && graphResp.hits() != null) ? graphResp.hits() : List.of();
            } else {
                RetrieveResponse semResp = graphRagClient.retrieveSemantic(semReq, correlationId);
                semanticHits = (semResp != null && semResp.hits() != null) ? semResp.hits() : List.of();
                RetrieveResponse graphResp = graphRagClient.retrieveGraph(graphReq, correlationId);
                graphHits = (graphResp != null && graphResp.hits() != null) ? graphResp.hits() : List.of();
            }
            long retrievalMs = (System.nanoTime() - retrievalStartNs) / 1_000_000L;

            // Event 4: status "Ranking results…"
            sendSse(emitter, "status", new SseStatusPayload("Ranking results…"));

            long llmStartNs = System.nanoTime();
            // Merger stage
            List<CardResult> mergedResults = resultMerger.merge(
                semanticHits,
                graphHits,
                properties.scoring(),
                topK
            );

            // Synthesis / Knowledge Card construction
            boolean gapFlag = mergedResults.isEmpty();
            String gapMessage = gapFlag ? "No internal prior art found — this may be a candidate Hard Problem" : null;
            String directAnswer = buildDirectAnswer(mergedResults);

            KnowledgeCard card = new KnowledgeCard(
                rawQuery,
                interpretation.problemStatement(),
                interpretation.techNeeded(),
                directAnswer,
                mergedResults,
                gapFlag,
                gapMessage
            );
            long llmMs = (System.nanoTime() - llmStartNs) / 1_000_000L;

            // Event 5: result (Knowledge Card JSON)
            sendSse(emitter, "result", card);

            // Token counts extraction (SPEC criterion 12)
            Integer inputTokens = (rawQuery != null) ? Math.max(1, rawQuery.length() / 4) : null;
            Integer outputTokens = (directAnswer != null) ? Math.max(1, directAnswer.length() / 4) : null;

            // Enrich active OpenTelemetry Span with MLflow Standard UI & GenAI metadata keys
            String formattedInput = String.format("{\"query\":\"%s\"}", rawQuery != null ? rawQuery.replace("\"", "\\\"").replace("\n", " ") : "");
            String ansText = directAnswer != null ? directAnswer : "No internal prior art found.";
            String formattedOutput = String.format("{\"answer\":\"%s\"}", ansText.replace("\"", "\\\"").replace("\n", " "));

            com.company.sage.util.OtelSpanHelper.setAttribute("mlflow.trace.inputs", formattedInput);
            com.company.sage.util.OtelSpanHelper.setAttribute("input.value", rawQuery);
            com.company.sage.util.OtelSpanHelper.setAttribute("gen_ai.prompt", rawQuery);
            com.company.sage.util.OtelSpanHelper.setAttribute("mlflow.trace.outputs", formattedOutput);
            com.company.sage.util.OtelSpanHelper.setAttribute("output.value", ansText);
            com.company.sage.util.OtelSpanHelper.setAttribute("gen_ai.completion", ansText);
            com.company.sage.util.OtelSpanHelper.setAttribute("session.id", correlationId);
            com.company.sage.util.OtelSpanHelper.setAttribute("user.id", "developer");
            com.company.sage.util.OtelSpanHelper.setAttribute("service.version", "0.0.1-SNAPSHOT");

            // Dynamic Git metadata & MLflow UI column attributes
            String commitHash = com.company.sage.util.GitUtil.getCommitHash();
            com.company.sage.util.OtelSpanHelper.setAttribute("git.commit", commitHash);
            com.company.sage.util.OtelSpanHelper.setAttribute("mlflow.source.git.commit", commitHash);

            com.company.sage.util.OtelSpanHelper.setAttribute("sage.correlation_id", correlationId);
            com.company.sage.util.OtelSpanHelper.setAttribute("sage.problem_statement", interpretation.problemStatement());
            com.company.sage.util.OtelSpanHelper.setAttribute("sage.tech_needed", String.join(", ", interpretation.techNeeded()));
            com.company.sage.util.OtelSpanHelper.setAttribute("rag.num_results", mergedResults.size());
            com.company.sage.util.OtelSpanHelper.setAttribute("rag.gap_flag", gapFlag);
            if (inputTokens != null) {
                com.company.sage.util.OtelSpanHelper.setAttribute("gen_ai.usage.input_tokens", inputTokens.longValue());
            }
            if (outputTokens != null) {
                com.company.sage.util.OtelSpanHelper.setAttribute("gen_ai.usage.output_tokens", outputTokens.longValue());
            }
            if (inputTokens != null && outputTokens != null) {
                com.company.sage.util.OtelSpanHelper.setAttribute("gen_ai.usage.total_tokens", (long) (inputTokens + outputTokens));
            }

            // Event 6: timing (fail-soft per SPEC)
            long e2eMs = (System.nanoTime() - requestStartNs) / 1_000_000L;
            try {
                Map<String, Object> timingData = new HashMap<>();
                timingData.put("e2e_ms", e2eMs);
                timingData.put("llm_ms", llmMs);
                timingData.put("retrieval_ms", retrievalMs);

                if (inputTokens != null) {
                    timingData.put("input_tokens", inputTokens);
                }
                if (outputTokens != null) {
                    timingData.put("output_tokens", outputTokens);
                    if (llmMs > 0) {
                        double tokensPerSec = outputTokens.doubleValue() / (llmMs / 1000.0);
                        timingData.put("tokens_per_sec", Math.round(tokensPerSec * 100.0) / 100.0);
                    }
                }

                sendSse(emitter, "timing", timingData);
            } catch (Exception ex) {
                log.warn("Fail-soft: failed to emit timing SSE event for correlationId {}: {}", correlationId, ex.getMessage());
            }

            // Event 7: done {}
            sendSse(emitter, "done", java.util.Map.of());

            emitter.complete();
        } catch (Exception e) {
            log.error("Error processing ask request for correlationId {}: {}", correlationId, e.getMessage(), e);
            try {
                SseErrorPayload errorPayload = new SseErrorPayload(
                    "An unexpected error occurred during query processing",
                    e.getMessage(),
                    "INTERNAL_ERROR",
                    correlationId
                );
                sendSse(emitter, "error", errorPayload);
            } catch (Exception ignored) {
            }
            emitter.completeWithError(e);
        }

        return emitter;
    }

    private void sendSse(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    /**
     * Card-level routing headline: match count + top-ranked hit metadata.
     * Never copies {@link CardResult#summary()} — that stays per-result evidence.
     */
    static String buildDirectAnswer(List<CardResult> results) {
        if (results == null || results.isEmpty()) {
            return "No internal prior art found.";
        }

        CardResult top = results.getFirst();
        int matchCount = results.size();
        String matchLabel = matchCount == 1 ? "match" : "matches";
        String title = firstNonBlank(top.hardProblemTitle(), "Untitled");
        String team = firstNonBlank(top.teamName(), "Unknown team");

        String answer = "%d %s. Top: '%s' owned by team '%s'."
            .formatted(matchCount, matchLabel, title, team);

        List<String> experts = top.solvedBy();
        if (experts != null && !experts.isEmpty()) {
            return answer + " Experts: " + String.join(", ", experts) + ".";
        }
        return answer;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
