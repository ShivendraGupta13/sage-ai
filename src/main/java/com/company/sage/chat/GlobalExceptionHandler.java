package com.company.sage.chat;

import com.company.sage.model.ApiErrorResponse;
import com.company.sage.model.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
