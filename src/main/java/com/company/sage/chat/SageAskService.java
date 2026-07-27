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
            RetrieveResponse semResp = graphRagClient.retrieveSemantic(semReq, correlationId);
            List<RetrieveHit> semanticHits = (semResp != null && semResp.hits() != null) ? semResp.hits() : List.of();

            GraphRetrieveRequest graphReq = new GraphRetrieveRequest(interpretation.techNeeded(), topK);
            RetrieveResponse graphResp = graphRagClient.retrieveGraph(graphReq, correlationId);
            List<RetrieveHit> graphHits = (graphResp != null && graphResp.hits() != null) ? graphResp.hits() : List.of();

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
            String directAnswer = !gapFlag && !mergedResults.isEmpty()
                ? (mergedResults.get(0).summary() != null ? mergedResults.get(0).summary() : mergedResults.get(0).hardProblemTitle())
                : "No internal prior art found.";

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
}
