package com.company.sage.adk.tools;

import com.company.sage.config.SageProperties;
import com.company.sage.merge.ResultMerger;
import com.company.sage.model.CardResult;
import com.company.sage.model.RetrieveHit;
import com.google.adk.sessions.State;
import com.google.adk.tools.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultMergerToolTest {

    @Mock
    private ResultMerger resultMerger;

    @Mock
    private ToolContext toolContext;

    private SageProperties properties;
    private State state;
    private ResultMergerTool tool;

    @BeforeEach
    void setUp() {
        properties = new SageProperties(
            new SageProperties.GraphRag("http://localhost:8000"),
            new SageProperties.Adk(new SageProperties.Adk.Llm("http://localhost:11434", "llama3.2", 0.0)),
            new SageProperties.Retrieval(5, 0.60),
            new SageProperties.Scoring(0.6, 0.4, 0.1, 0.60)
        );

        state = new State(new HashMap<>());
        tool = new ResultMergerTool(resultMerger, properties);
    }

    @Test
    void shouldReadHitsFromStateCallMergerAndWriteMergedHits() {
        RetrieveHit sHit = new RetrieveHit("DOC-1", "Title 1", 0.8, 0.0, List.of("semantic"), "Passage 1", "Source 1", null);
        RetrieveHit gHit = new RetrieveHit("DOC-2", "Title 2", 0.0, 0.9, List.of("graph"), "Passage 2", "Source 2", null);

        state.put("semantic_hits", List.of(sHit));
        state.put("graph_hits", List.of(gHit));
        when(toolContext.state()).thenReturn(state);

        CardResult expectedCard = new CardResult(
            1, 0.8, List.of("semantic"), "Team Alpha", "Title 1", "Security", List.of("Person A"), "Passage 1", "https://link", "Evidence", List.of("Source 1")
        );
        when(resultMerger.merge(anyList(), anyList(), any(), anyInt())).thenReturn(List.of(expectedCard));

        List<CardResult> merged = tool.merge(toolContext);

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).hardProblemTitle()).isEqualTo("Title 1");
        assertThat(state.get("merged_hits")).isNotNull();
    }
}
