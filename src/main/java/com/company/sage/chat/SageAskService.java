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

            // Event 4: status "Ranking results…"
            sendSse(emitter, "status", new SseStatusPayload("Ranking results…"));

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

            // Event 5: result (Knowledge Card JSON)
            sendSse(emitter, "result", card);

            // Event 6: done {}
            sendSse(emitter, "done", Map.of());

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
