package com.company.sage.util;

import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fail-soft helper utility for attaching GenAI, RAG, and business metadata attributes
 * to the active OpenTelemetry span without risking runtime exceptions.
 */
public final class OtelSpanHelper {

    private static final Logger log = LoggerFactory.getLogger(OtelSpanHelper.class);

    private OtelSpanHelper() {}

    public static void setAttribute(String key, String value) {
        if (key == null || value == null) return;
        try {
            Span current = Span.current();
            if (current != null && current.isRecording()) {
                current.setAttribute(key, value);
            }
        } catch (Throwable t) {
            log.debug("Fail-soft: Failed to record span attribute {}: {}", key, t.getMessage());
        }
    }

    public static void setAttribute(String key, long value) {
        if (key == null) return;
        try {
            Span current = Span.current();
            if (current != null && current.isRecording()) {
                current.setAttribute(key, value);
            }
        } catch (Throwable t) {
            log.debug("Fail-soft: Failed to record span attribute {}: {}", key, t.getMessage());
        }
    }

    public static void setAttribute(String key, boolean value) {
        if (key == null) return;
        try {
            Span current = Span.current();
            if (current != null && current.isRecording()) {
                current.setAttribute(key, value);
            }
        } catch (Throwable t) {
            log.debug("Fail-soft: Failed to record span attribute {}: {}", key, t.getMessage());
        }
    }
}
