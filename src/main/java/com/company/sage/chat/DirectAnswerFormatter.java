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

        Set<String> teams = new LinkedHashSet<>();
        CardResult attributed = null;
        for (CardResult r : results) {
            String name = realTeam(r.teamName());
            if (name != null) {
                teams.add(name);
                if (attributed == null) {
                    attributed = r;
                }
            }
        }

        if (teams.isEmpty()) {
            return "Yes — prior art found: " + displayTitle(results.getFirst()) + ".";
        }

        String team = realTeam(attributed.teamName());
        String title = displayTitle(attributed);
        int teamCount = teams.size();
        String verb = teamCount == 1 ? "team has" : "teams have";

        return String.format(
            "Yes — %d %s solved this: %s (%s).",
            teamCount,
            verb,
            team,
            title
        );
    }

    /** Non-blank team that is not the placeholder "N/A"; otherwise null. */
    private static String realTeam(String teamName) {
        if (teamName == null || teamName.isBlank() || "N/A".equalsIgnoreCase(teamName.trim())) {
            return null;
        }
        return teamName.trim();
    }

    private static String displayTitle(CardResult result) {
        String title = result.hardProblemTitle();
        if (title == null || title.isBlank() || "Untitled".equalsIgnoreCase(title.trim())) {
            return "Untitled";
        }
        return title.trim();
    }
}
