package com.company.sage.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SagePropertiesTest {

    @Autowired
    private GraphRagProperties graphRagProperties;

    @Autowired
    private LlmProperties llmProperties;

    @Autowired
    private RetrievalProperties retrievalProperties;

    @Autowired
    private ScoringProperties scoringProperties;

    @Test
    void propertiesAreLoadedAndMatchYamlDefaults() {
        assertThat(graphRagProperties).isNotNull();
        assertThat(graphRagProperties.getBaseUrl()).isEqualTo("http://localhost:8000");

        assertThat(llmProperties).isNotNull();
        assertThat(llmProperties.getBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(llmProperties.getModelName()).isEqualTo("llama3.2");

        assertThat(retrievalProperties).isNotNull();
        assertThat(retrievalProperties.getTopK()).isEqualTo(5);
        assertThat(retrievalProperties.getMinScore()).isEqualTo(0.60);

        assertThat(scoringProperties).isNotNull();
        assertThat(scoringProperties.getW1()).isEqualTo(0.6);
        assertThat(scoringProperties.getW2()).isEqualTo(0.4);
        assertThat(scoringProperties.getDualMatchBoost()).isEqualTo(0.1);
        assertThat(scoringProperties.getMinScore()).isEqualTo(0.60);
    }
}
