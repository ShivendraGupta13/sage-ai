package com.company.sage.config;

import com.google.adk.models.BaseLlm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LlmConfigTest {

    @Autowired
    private BaseLlm adkLlm;

    @Test
    void shouldLoadAdkLlmBeanInContext() {
        assertThat(adkLlm).isNotNull();
        assertThat(adkLlm.model()).isEqualTo("llama3.2");
    }
}
