package com.company.sage.adk.agents;

import com.company.sage.adk.tools.GraphTraversalTool;
import com.company.sage.adk.tools.SemanticSearchTool;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.models.BaseLlm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RetrieveAgentsTest {

    @Mock
    private BaseLlm adkLlm;

    @Mock
    private SemanticSearchTool semanticTool;

    @Mock
    private GraphTraversalTool graphTool;

    @Test
    void shouldBuildSemanticAndGraphAgentsAndParallelRetrieveAgent() {
        LlmAgent semanticAgent = SemanticSearchAgent.create(adkLlm, semanticTool);
        LlmAgent graphAgent = GraphTraversalAgent.create(adkLlm, graphTool);
        ParallelAgent parallelAgent = ParallelRetrieveAgent.create(semanticAgent, graphAgent);

        assertThat(semanticAgent).isNotNull();
        assertThat(semanticAgent.name()).isEqualTo("SemanticSearchAgent");

        assertThat(graphAgent).isNotNull();
        assertThat(graphAgent.name()).isEqualTo("GraphTraversalAgent");

        assertThat(parallelAgent).isNotNull();
        assertThat(parallelAgent.name()).isEqualTo("ParallelRetrieve");
        assertThat(parallelAgent.subAgents()).hasSize(2);
    }
}
