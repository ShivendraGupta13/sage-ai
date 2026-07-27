package com.company.sage.chat;

import com.company.sage.model.ApiErrorResponse;
import com.company.sage.model.AskRequest;
import com.company.sage.model.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class SpringChatController {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private final SageAskService askService;

    public SpringChatController(SageAskService askService) {
        this.askService = askService;
    }

    @PostMapping(
        value = "/ask",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = { MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE }
    )
    public ResponseEntity<?> ask(
        @RequestBody(required = false) AskRequest request,
        @RequestHeader(name = CORRELATION_HEADER, required = false) String correlationHeader
    ) {
        String correlationId = (correlationHeader != null && !correlationHeader.isBlank())
            ? correlationHeader
            : UUID.randomUUID().toString();

        if (request == null || request.query() == null || request.query().isBlank()) {
            String rejectedVal = (request != null) ? request.query() : null;
            ApiErrorResponse error = new ApiErrorResponse(
                "about:blank",
                "Bad Request",
                400,
                "INVALID_REQUEST",
                "Validation failed: query is required",
                "The query field in the request body cannot be blank.",
                "/ask",
                correlationId,
                List.of(new FieldErrorDetail("query", "query must not be blank", rejectedVal))
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error);
        }

        SseEmitter emitter = askService.processAsk(request, correlationId);

        // Post-review enhancement: Pass X-Correlation-Id header back on successful HTTP 200 response for client tracking
        return ResponseEntity.ok()
            .header(CORRELATION_HEADER, correlationId)
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(emitter);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
        HttpMessageNotReadableException ex,
        HttpServletRequest request
    ) {
        String correlationId = UUID.randomUUID().toString();
        ApiErrorResponse error = new ApiErrorResponse(
            "about:blank",
            "Bad Request",
            400,
            "INVALID_REQUEST",
            "Validation failed: Malformed JSON or empty request body",
            ex.getMessage(),
            request != null ? request.getRequestURI() : "/ask",
            correlationId,
            List.of(new FieldErrorDetail("query", "query must not be blank", null))
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body(error);
    }
}
