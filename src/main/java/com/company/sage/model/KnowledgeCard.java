package com.company.sage.model;

import java.util.List;

public record KnowledgeCard(
    String query,
    String problemStatement,
    List<String> techNeeded,
    String directAnswer,
    List<CardResult> results,
    boolean gapFlag,
    String gapMessage
) {
    public KnowledgeCard {
        if (techNeeded == null) techNeeded = List.of();
        if (results == null) results = List.of();
    }
}
