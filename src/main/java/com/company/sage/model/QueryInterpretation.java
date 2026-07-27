package com.company.sage.model;

import java.util.List;

public record QueryInterpretation(
    String problemStatement,
    List<String> techNeeded
) {
    public QueryInterpretation {
        if (problemStatement == null) problemStatement = "";
        if (techNeeded == null) techNeeded = List.of();
    }
}
