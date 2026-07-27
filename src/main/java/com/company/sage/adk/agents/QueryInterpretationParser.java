package com.company.sage.adk.agents;

import com.company.sage.model.QueryInterpretation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class QueryInterpretationParser {

    private static final Logger log = LoggerFactory.getLogger(QueryInterpretationParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QueryInterpretationParser() {}

    public static QueryInterpretation parse(String rawOutput, String fallbackQuery) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return new QueryInterpretation(fallbackQuery != null ? fallbackQuery : "", List.of());
        }

        String cleaned = rawOutput.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }

        // Post-review enhancement: Extract JSON substring if surrounded by conversational explanation text
        if (!cleaned.startsWith("{")) {
            int firstBrace = cleaned.indexOf('{');
            int lastBrace = cleaned.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                cleaned = cleaned.substring(firstBrace, lastBrace + 1).trim();
            }
        }

        try {
            QueryInterpretation parsed = MAPPER.readValue(cleaned, QueryInterpretation.class);
            String ps = (parsed.problemStatement() != null && !parsed.problemStatement().isBlank())
                ? parsed.problemStatement()
                : (fallbackQuery != null ? fallbackQuery : "");
            return new QueryInterpretation(ps, parsed.techNeeded());
        } catch (Exception e) {
            log.warn("Failed to parse QueryInterpret output: '{}'. Falling back to raw query.", rawOutput, e);
            return new QueryInterpretation(fallbackQuery != null ? fallbackQuery : "", List.of());
        }
    }
}
