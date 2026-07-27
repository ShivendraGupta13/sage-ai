package com.company.sage.adk.agents;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class KnowledgeCardSynthAgentTest {

    @Mock
    private BaseLlm adkLlm;

    @Test
    void shouldBuildKnowledgeCardSynthAgentWithCorrectNameAndOutputKey() {
        LlmAgent agent = KnowledgeCardSynthAgent.create(adkLlm);

        assertThat(agent).isNotNull();
        assertThat(agent.name()).isEqualTo("KnowledgeCardSynth");
        assertThat(agent.outputKey()).contains("knowledge_card");
    }
}
