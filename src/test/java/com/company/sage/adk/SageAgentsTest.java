package com.company.sage.adk;

import com.google.adk.agents.SequentialAgent;
import com.google.adk.models.BaseLlm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SageAgentsTest {

    @Autowired
    private SequentialAgent sageRootAgent;

    @Autowired
    private BaseLlm adkLlm;

    @Test
    void shouldAssembleSageRootAgentWithFourSequentialSteps() {
        assertThat(sageRootAgent).isNotNull();
        assertThat(sageRootAgent.name()).isEqualTo("SageRoot");
        assertThat(sageRootAgent.subAgents()).hasSize(4);
    }
}
