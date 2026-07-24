package com.company.sage.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/**
 * ADK FunctionTool-compatible list argument that Jackson can bind from either a JSON
 * array or a string (JSON array text / comma-separated). Avoids ADK's hard failure when
 * {@code List} parameters arrive as {@code VALUE_STRING}.
 */
public final class FlexibleStringList {

    private final List<String> values;

    private FlexibleStringList(List<String> values) {
        this.values = values == null ? List.of() : List.copyOf(values);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static FlexibleStringList from(Object raw) {
        return new FlexibleStringList(ToolArgs.asStringList(raw));
    }

    public static FlexibleStringList of(List<String> values) {
        return new FlexibleStringList(values);
    }

    @JsonValue
    public List<String> asList() {
        return values;
    }
}
