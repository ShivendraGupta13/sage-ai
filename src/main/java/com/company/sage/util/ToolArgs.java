package com.company.sage.util;

import com.company.sage.model.RetrieveHit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Coerces LLM / ADK tool arguments that may arrive as JSON arrays, JSON strings,
 * or comma-separated text into typed lists. ADK's FunctionTool fails hard when a
 * {@code List} parameter is bound from a JSON string.
 */
public final class ToolArgs {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolArgs() {
    }

    /**
     * Coerces {@code value} into a list of strings.
     * Accepts a {@link List}, a JSON array string, or a comma-separated string.
     * Returns an empty list for null / blank / unparseable input.
     */
    public static List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(e -> e != null)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        if (value instanceof Map<?, ?> map) {
            Object nested = map.containsKey("result") ? map.get("result") : map.get("values");
            if (nested != null && nested != value) {
                return asStringList(nested);
            }
        }
        if (value instanceof FlexibleStringList flexible) {
            return flexible.asList();
        }
        String text = LlmJson.stripCodeFences(value.toString());
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<Object> parsed = MAPPER.readValue(trimmed, new TypeReference<List<Object>>() {});
                return asStringList(parsed);
            } catch (Exception e) {
                return List.of();
            }
        }
        return Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Coerces session / tool payload into {@link RetrieveHit} list.
     * Accepts a list of maps/hits, a JSON array string, or ADK's {@code {"result": [...]}} wrapper.
     * Returns an empty list for null / unparseable input.
     */
    public static List<RetrieveHit> asHitList(Object value) {
        if (value == null) {
            return List.of();
        }
        Object unwrapped = unwrapResult(value);
        if (unwrapped instanceof List<?> list) {
            if (list.isEmpty()) {
                return List.of();
            }
            List<RetrieveHit> hits = new ArrayList<>(list.size());
            for (Object element : list) {
                if (element instanceof RetrieveHit hit) {
                    hits.add(hit);
                } else if (element instanceof Map) {
                    hits.add(MAPPER.convertValue(element, RetrieveHit.class));
                } else if (element != null) {
                    try {
                        hits.add(MAPPER.convertValue(element, RetrieveHit.class));
                    } catch (IllegalArgumentException e) {
                        // skip malformed element
                    }
                }
            }
            return hits;
        }
        if (unwrapped instanceof String || !(unwrapped instanceof Map)) {
            String text = LlmJson.extractJsonPayload(unwrapped.toString());
            if (text == null || text.isBlank()) {
                return List.of();
            }
            String trimmed = text.trim();
            if (!trimmed.startsWith("[")) {
                return List.of();
            }
            try {
                List<Object> parsed = MAPPER.readValue(trimmed, new TypeReference<List<Object>>() {});
                return asHitList(parsed);
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of();
    }

    private static Object unwrapResult(Object value) {
        if (value instanceof Map<?, ?> map && map.containsKey("result") && map.size() <= 2) {
            return map.get("result");
        }
        return value;
    }
}
