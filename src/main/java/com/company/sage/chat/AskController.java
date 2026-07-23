package com.company.sage.chat;

import com.company.sage.model.AskErrorPayload;
import com.company.sage.model.AskRequest;
import com.company.sage.model.AskStatusPayload;
import com.company.sage.model.ErrorResponse;
import com.company.sage.model.ValidationError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionKey;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST Controller exposing the POST /ask SSE streaming bridge.
 */
@RestController
public class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);

    private final SequentialAgent sageRootAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AskController(SequentialAgent sageRootAgent) {
        this.sageRootAgent = sageRootAgent;
    }

    /**
     * Accepts a user query and returns an SSE event stream representing the ADK orchestration progress.
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<?> ask(
            @RequestBody(required = false) AskRequest request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String headerCorrId,
            HttpServletRequest httpRequest) {

        String correlationId = headerCorrId != null && !headerCorrId.trim().isEmpty()
                ? headerCorrId.trim()
                : UUID.randomUUID().toString();

        if (request == null || request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            List<ValidationError> validationErrors = List.of(
                new ValidationError("query", "query must not be empty", request != null ? request.getQuery() : null)
            );
            ErrorResponse error = new ErrorResponse(
                "about:blank",
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                "Validation failed",
                "Query parameter is required and must not be empty.",
                httpRequest.getRequestURI(),
                correlationId,
                validationErrors
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error);
        }

        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout

        InMemoryRunner runner = new InMemoryRunner(sageRootAgent, "sage");
        SessionKey sessionKey = new SessionKey("sage", "anonymous", correlationId);

        // Track state execution boundaries
        AtomicBoolean queryInterpreted = new AtomicBoolean(false);
        AtomicBoolean searchStarted = new AtomicBoolean(false);
        AtomicBoolean rankingStarted = new AtomicBoolean(false);

        // 1. Immediately emit status: interpreting query
        try {
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(new AskStatusPayload("Interpreting query…")));
        } catch (IOException e) {
            log.warn("Failed to send initial status event: {}", e.getMessage());
            emitter.completeWithError(e);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(emitter);
        }

        // Run the agent pipeline sequentially and stream output events
        runner.sessionService().createSession(sessionKey)
            .flatMapPublisher(session -> runner.runAsync(sessionKey, Content.fromParts(Part.fromText(request.getQuery()))))
            .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
            .subscribe(
                event -> {
                    try {
                        String author = event.author();
                        boolean turnComplete = event.turnComplete().orElse(false) || event.finalResponse();

                        if ("QueryInterpret".equals(author) && turnComplete && queryInterpreted.compareAndSet(false, true)) {
                            Session s = runner.sessionService().getSession(sessionKey, GetSessionConfig.builder().build()).blockingGet();
                            if (s != null && s.state().containsKey("query_interpretation")) {
                                String interpretationJson = s.state().get("query_interpretation").toString();
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> interpretation = objectMapper.readValue(interpretationJson, Map.class);
                                    String problem = (String) interpretation.get("problemStatement");
                                    @SuppressWarnings("unchecked")
                                    List<String> tech = (List<String>) interpretation.get("techNeeded");

                                    // 2. Emit status: problem state and tech context identified
                                    emitter.send(SseEmitter.event()
                                            .name("status")
                                            .data(new AskStatusPayload("Identified problem state and tech context", problem, tech)));
                                } catch (Exception e) {
                                    log.warn("Failed to parse query_interpretation JSON: {}", e.getMessage());
                                    // Fallback to simple status event
                                    emitter.send(SseEmitter.event()
                                            .name("status")
                                            .data(new AskStatusPayload("Identified problem state and tech context")));
                                }
                            }
                            
                            // 3. Immediately emit status: search starting
                            if (searchStarted.compareAndSet(false, true)) {
                                emitter.send(SseEmitter.event()
                                        .name("status")
                                        .data(new AskStatusPayload("Searching knowledge base…")));
                            }
                        } else if ("ParallelRetrieve".equals(author) && turnComplete && rankingStarted.compareAndSet(false, true)) {
                            // 4. Emit status: ranking results
                            emitter.send(SseEmitter.event()
                                    .name("status")
                                    .data(new AskStatusPayload("Ranking results…")));
                        } else if ("KnowledgeCardSynth".equals(author) && turnComplete) {
                            Session s = runner.sessionService().getSession(sessionKey, GetSessionConfig.builder().build()).blockingGet();
                            if (s != null && s.state().containsKey("knowledge_card")) {
                                Object cardRaw = s.state().get("knowledge_card");
                                Object cardParsed = cleanAndParseJson(cardRaw);
                                
                                // 5. Emit final result payload
                                emitter.send(SseEmitter.event()
                                        .name("result")
                                        .data(cardParsed));
                            }
                        }
                    } catch (IOException ex) {
                        log.warn("Client disconnected during execution stream: {}", ex.getMessage());
                    }
                },
                err -> {
                    log.error("Pipeline execution encountered an unrecoverable failure", err);
                    try {
                        // 6. Emit error payload
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(new AskErrorPayload("Execution failed", err.getMessage(), "500", correlationId)));
                    } catch (IOException e) {
                        log.warn("Failed to send error event to disconnected client: {}", e.getMessage());
                    } finally {
                        emitter.complete();
                    }
                },
                () -> {
                    try {
                        // 7. Emit done payload
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data("{}"));
                    } catch (IOException ex) {
                        log.warn("Failed to send done event: {}", ex.getMessage());
                    } finally {
                        emitter.complete();
                    }
                }
            );

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    private Object cleanAndParseJson(Object raw) {
        if (raw == null) return null;
        String str = raw.toString().trim();
        if (str.startsWith("```json")) {
            str = str.substring(7);
        } else if (str.startsWith("```")) {
            str = str.substring(3);
        }
        if (str.endsWith("```")) {
            str = str.substring(0, str.length() - 3);
        }
        str = str.trim();
        try {
            return objectMapper.readValue(str, Object.class);
        } catch (Exception e) {
            log.warn("Failed to parse Knowledge Card JSON, returning raw string: {}", e.getMessage());
            return str;
        }
    }
}
