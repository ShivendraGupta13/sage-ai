package com.company.sage.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/**
 * Helpers for parsing LLM outputs that may be wrapped in markdown or prose.
 */
public final class LlmJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmJson() {
    }

    /**
     * Strips optional {@code ```json} / {@code ```} fences and returns trimmed JSON text.
     * If fences appear mid-string, extracts the first fenced block.
     */
    public static String stripCodeFences(String raw) {
        if (raw == null) {
            return null;
        }
        String str = raw.trim();
        int fenceStart = str.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = str.indexOf('\n', fenceStart);
            if (contentStart < 0) {
                contentStart = fenceStart + 3;
                if (str.regionMatches(true, contentStart, "json", 0, 4)) {
                    contentStart += 4;
                }
            } else {
                contentStart += 1;
            }
            int fenceEnd = str.indexOf("```", contentStart);
            if (fenceEnd > contentStart) {
                str = str.substring(contentStart, fenceEnd).trim();
            } else {
                str = str.substring(contentStart).trim();
            }
        }
        if (str.startsWith("```json")) {
            str = str.substring(7);
        } else if (str.startsWith("```")) {
            str = str.substring(3);
        }
        if (str.endsWith("```")) {
            str = str.substring(0, str.length() - 3);
        }
        return str.trim();
    }

    /**
     * Returns the first top-level JSON object/array substring, or the trimmed input.
     */
    public static String extractJsonPayload(String raw) {
        String stripped = stripCodeFences(raw);
        if (stripped == null || stripped.isEmpty()) {
            return stripped;
        }
        int objectStart = stripped.indexOf('{');
        int arrayStart = stripped.indexOf('[');
        int start;
        char open;
        char close;
        if (objectStart < 0 && arrayStart < 0) {
            return stripped;
        }
        if (objectStart < 0 || (arrayStart >= 0 && arrayStart < objectStart)) {
            start = arrayStart;
            open = '[';
            close = ']';
        } else {
            start = objectStart;
            open = '{';
            close = '}';
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return stripped.substring(start, i + 1);
                }
            }
        }
        return stripped;
    }

    /**
     * Parses LLM JSON into a generic object after stripping markdown fences / extracting JSON.
     * On parse failure returns the stripped (or original) string.
     */
    public static Object parseOrRaw(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map || raw instanceof List) {
            return raw;
        }
        String payload = extractJsonPayload(raw.toString());
        try {
            return MAPPER.readValue(payload, Object.class);
        } catch (Exception e) {
            return payload;
        }
    }

    /**
     * Parses LLM JSON into a map after stripping markdown fences / extracting JSON.
     *
     * @throws Exception if the content is not a JSON object
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseMap(Object raw) throws Exception {
        if (raw == null) {
            throw new IllegalArgumentException("raw must not be null");
        }
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        String payload = extractJsonPayload(raw.toString());
        return MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
    }
}
