package com.company.sage.chat;

import com.company.sage.model.CardResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class DirectAnswerFormatter {

    private DirectAnswerFormatter() {}

    static String format(List<CardResult> results) {
        if (results == null || results.isEmpty()) {
            return "No internal prior art found.";
        }

        CardResult top = results.getFirst();
        String team = blankToNa(top.teamName());
        String title = blankToUntitled(top.hardProblemTitle());

        Set<String> teams = new LinkedHashSet<>();
        for (CardResult r : results) {
            String name = blankToNa(r.teamName());
            if (!"N/A".equals(name)) {
                teams.add(name);
            }
        }
        int teamCount = Math.max(1, teams.size());
        String verb = teamCount == 1 ? "team has" : "teams have";

        return String.format(
            "Yes — %d %s solved this: %s (%s).",
            teamCount,
            verb,
            team,
            title
        );
    }

    private static String blankToNa(String teamName) {
        return (teamName == null || teamName.isBlank()) ? "N/A" : teamName;
    }

    private static String blankToUntitled(String title) {
        return (title == null || title.isBlank()) ? "Untitled" : title;
    }
}
