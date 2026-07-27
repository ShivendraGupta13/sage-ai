package com.company.sage.chat;

import com.company.sage.model.RetrieveHit;
import com.company.sage.model.RetrieveMetadata;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class TechNeededRecovery {

    private TechNeededRecovery() {}

    static List<String> fromSemanticHits(List<RetrieveHit> semanticHits) {
        if (semanticHits == null || semanticHits.isEmpty()) {
            return List.of();
        }
        Set<String> recovered = new LinkedHashSet<>();
        for (RetrieveHit hit : semanticHits) {
            if (hit == null) {
                continue;
            }
            RetrieveMetadata meta = hit.metadata();
            if (meta == null || meta.technologies() == null) {
                continue;
            }
            for (String tech : meta.technologies()) {
                if (tech != null && !tech.isBlank()) {
                    recovered.add(tech.trim());
                }
            }
        }
        return new ArrayList<>(recovered);
    }
}
