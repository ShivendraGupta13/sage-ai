package com.company.sage.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SagePropertiesTest {

    @Autowired
    private SageProperties sageProperties;

    @Test
    void shouldBindYamlDefaultsCorrectly() {
        assertThat(sageProperties).isNotNull();

        assertThat(sageProperties.graphRag()).isNotNull();
        assertThat(sageProperties.graphRag().baseUrl()).isEqualTo("http://localhost:8000");

        assertThat(sageProperties.adk()).isNotNull();
        assertThat(sageProperties.adk().llm()).isNotNull();
        assertThat(sageProperties.adk().llm().baseUrl()).isEqualTo("http://localhost:11434");
        assertThat(sageProperties.adk().llm().modelName()).isEqualTo("llama3.2");

        assertThat(sageProperties.retrieval()).isNotNull();
        assertThat(sageProperties.retrieval().topK()).isEqualTo(5);
        assertThat(sageProperties.retrieval().minScore()).isEqualTo(0.60);

        assertThat(sageProperties.scoring()).isNotNull();
        assertThat(sageProperties.scoring().w1()).isEqualTo(0.6);
        assertThat(sageProperties.scoring().w2()).isEqualTo(0.4);
        assertThat(sageProperties.scoring().dualMatchBoost()).isEqualTo(0.1);
        assertThat(sageProperties.scoring().minScore()).isEqualTo(0.60);
    }
}
