package com.company.sage.chat;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.config.SageProperties;
import com.company.sage.merge.ResultMerger;
import com.company.sage.model.*;
import com.company.sage.adk.agents.QueryInterpretAgent;
import com.company.sage.adk.agents.QueryInterpretationParser;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;

/**
 * SageAskService orchestrates the ask-time execution pipeline.
 * Architectural Pipeline Alignment :
 * 1. Query Interpretation: QueryInterpretAgent (LlmAgent) infers precise technical tags (techNeeded)
 *    and rephrases the problem statement.
 * 2. Parallel Search Fan-out: Parallel retrieve calls to Python Graph RAG (/retrieve/semantic & /retrieve/graph).
 * 3. Deterministic Result Merger: ResultMerger scores and ranks hits based on vector similarity,
 *    graph match, and dual-match boost.
 * 4. Knowledge Card Synthesis: KnowledgeCardSynthAgent (LlmAgent) synthesizes direct solutions when online.
 * 5. Fail-Soft Resiliency : If Ollama is offline/unavailable, fallback keyword extraction
 *    and clear evidence-based fallback formatting are used so search results are never blocked.
 */
@Service
public class SageAskService {

    private static final Logger log = LoggerFactory.getLogger(SageAskService.class);
    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final GraphRagClient graphRagClient;
    private final ResultMerger resultMerger;
    private final SageProperties properties;
    private final BaseLlm adkLlm;
    private final SequentialAgent sageRootAgent;

    public SageAskService(
            GraphRagClient graphRagClient,
            ResultMerger resultMerger,
            SageProperties properties
    ) {
        this(graphRagClient, resultMerger, properties, null, null);
    }

    public SageAskService(
            GraphRagClient graphRagClient,
            ResultMerger resultMerger,
            SageProperties properties,
            BaseLlm adkLlm
    ) {
        this(graphRagClient, resultMerger, properties, adkLlm, null);
    }

    @Autowired
    public SageAskService(
            GraphRagClient graphRagClient,
            ResultMerger resultMerger,
            SageProperties properties,
            @Autowired(required = false) BaseLlm adkLlm,
            @Autowired(required = false) SequentialAgent sageRootAgent
    ) {
        this.graphRagClient = graphRagClient;
        this.resultMerger = resultMerger;
        this.properties = properties;
        this.adkLlm = adkLlm;
        this.sageRootAgent = sageRootAgent;
    }

    public SseEmitter processAsk(AskRequest request, String correlationId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        try {
            // Event 1: status "Interpreting query…"
            sendSse(emitter, "status", new SseStatusPayload("Interpreting query…"));

            String rawQuery = request.query();

            // Stage 1: Query Interpretation via LLM (QueryInterpretAgent prompt) with fail-soft fallback
            QueryInterpretation interpretation = interpretQuery(rawQuery);

            // Event 2: status "Identified problem state and tech context"
            sendSse(emitter, "status", new SseStatusPayload(
                    "Identified problem state and tech context",
                    interpretation.problemStatement(),
                    interpretation.techNeeded()
            ));

            // Event 3: status "Searching knowledge base…"
            sendSse(emitter, "status", new SseStatusPayload("Searching knowledge base…"));

            // Stage 2: Retrieval (Semantic + Graph fan-out to Python Graph RAG)
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

            // Stage 3: Deterministic Result Merger
            List<CardResult> mergedResults = resultMerger.merge(
                    semanticHits,
                    graphHits,
                    properties.scoring(),
                    topK
            );

            // Stage 4: Synthesis & Knowledge Card Construction (Option A + Option C Fail-Soft)
            boolean gapFlag = mergedResults.isEmpty();
            String gapMessage = gapFlag ? "No internal prior art found — this may be a candidate Hard Problem" : null;
            String directAnswer = gapFlag ? "No internal prior art found." : synthesizeDirectAnswer(rawQuery, mergedResults);

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

    /**
     * Interprets user query via QueryInterpretAgent LLM prompt if Ollama is reachable,
     * otherwise applies fail-soft fallback keyword extraction.
     */
    private QueryInterpretation interpretQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return new QueryInterpretation("", List.of());
        }
        if (adkLlm == null) {
            return fallbackInterpretation(rawQuery);
        }
        try {
            String prompt = QueryInterpretAgent.INSTRUCTION + "\n\nUser Question:\n" + rawQuery;
            LlmRequest request = LlmRequest.builder()
                    .contents(List.of(
                            Content.builder()
                                    .role("user")
                                    .parts(List.of(Part.fromText(prompt)))
                                    .build()
                    ))
                    .build();

            LlmResponse response = adkLlm.generateContent(request, false).blockingFirst();
            String outputText = (response != null && response.content() != null && response.content().isPresent())
                    ? response.content().get().text()
                    : null;

            QueryInterpretation parsed = QueryInterpretationParser.parse(outputText, rawQuery);
            if (parsed.techNeeded() == null || parsed.techNeeded().isEmpty()) {
                List<String> keywords = extractTechKeywords(rawQuery);
                return new QueryInterpretation(parsed.problemStatement(), keywords);
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Ollama LLM query interpretation failed: {}. Using fail-soft fallback.", e.getMessage());
            return fallbackInterpretation(rawQuery);
        }
    }

    private QueryInterpretation fallbackInterpretation(String rawQuery) {
        List<String> techNeeded = extractTechKeywords(rawQuery);
        return new QueryInterpretation(rawQuery, techNeeded);
    }

    /**
     * Synthesizes 1-2 sentence direct solution using KnowledgeCardSynthAgent prompt if Ollama is reachable.
     * Uses Option C fallback formatting if Ollama is offline so search results are never blocked.
     */
    private String synthesizeDirectAnswer(String rawQuery, List<CardResult> mergedResults) {
        if (mergedResults == null || mergedResults.isEmpty()) {
            return "No internal prior art found.";
        }

        String fallbackAnswer = fallbackDirectAnswer(mergedResults);
        if (adkLlm == null) {
            return fallbackAnswer;
        }

        try {
            StringBuilder passages = new StringBuilder();
            int limit = Math.min(mergedResults.size(), 3);
            for (int i = 0; i < limit; i++) {
                CardResult r = mergedResults.get(i);
                passages.append(String.format(Locale.ROOT, "- Solution #%d (%s, solved by %s): %s\n",
                        r.rank(),
                        r.hardProblemTitle() != null ? r.hardProblemTitle() : "Untitled",
                        (r.solvedBy() != null && !r.solvedBy().isEmpty()) ? String.join(", ", r.solvedBy()) : "Unknown",
                        r.summary() != null ? r.summary() : ""
                ));
            }

            String prompt = """
                Synthesize a concise 1-2 sentence direct solution answering who solved this problem and how.
                Rely strictly on the provided evidence below. Do not invent teams, people, or details not present in the evidence.

                User Question: %s

                Retrieved Evidence:
                %s

                Synthesized Answer (1-2 sentences):
                """.formatted(rawQuery, passages.toString());

            LlmRequest request = LlmRequest.builder()
                    .contents(List.of(
                            Content.builder()
                                    .role("user")
                                    .parts(List.of(Part.fromText(prompt)))
                                    .build()
                    ))
                    .build();

            LlmResponse response = adkLlm.generateContent(request, false).blockingFirst();
            String outputText = (response != null && response.content() != null && response.content().isPresent())
                    ? response.content().get().text()
                    : null;

            if (outputText != null && !outputText.isBlank()) {
                return outputText.trim();
            } else {
                return fallbackAnswer;
            }
        } catch (Exception e) {
            log.warn("Ollama LLM direct answer synthesis failed: {}. Using fallback summary.", e.getMessage());
            return fallbackAnswer;
        }
    }

    /**
     * Option C Fallback Formatting when LLM is offline or fails:
     * Provides an informative header indicating LLM synthesis is offline while displaying the top hit summary.
     */
    private String fallbackDirectAnswer(List<CardResult> mergedResults) {
        if (mergedResults == null || mergedResults.isEmpty()) {
            return "No internal prior art found.";
        }
        CardResult top = mergedResults.getFirst();
        String summary = (top.summary() != null && !top.summary().isBlank())
                ? top.summary()
                : (top.hardProblemTitle() != null ? top.hardProblemTitle() : "No internal prior art found.");
        
        return "Direct answer synthesized from top-ranked evidence summary (LLM synthesis offline):\n\n" + summary;
    }

    private List<String> extractTechKeywords(String query) {
        if (query == null || query.isBlank()) return List.of();
        Set<String> stopWords = Set.of(
                "how", "did", "we", "solve", "in", "of", "a", "an", "the", "for", "to",
                "is", "on", "at", "by", "with", "from", "and", "or", "what", "which", "are", "do", "does", "implementation"
        );
        String[] tokens = query.split("[^a-zA-Z0-9+#]+");
        List<String> keywords = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() > 1 && !stopWords.contains(token.toLowerCase())) {
                keywords.add(token);
            }
        }
        return keywords;
    }

    private void sendSse(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }
}
