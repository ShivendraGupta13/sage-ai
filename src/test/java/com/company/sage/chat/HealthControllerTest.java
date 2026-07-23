package com.company.sage.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.model.GraphRagHealthResponse;
import com.company.sage.model.HealthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    private GraphRagClient graphRagClient;
    private HealthController healthController;

    @BeforeEach
    void setUp() {
        graphRagClient = mock(GraphRagClient.class);
        healthController = new HealthController(graphRagClient);
    }

    @Test
    void testGetHealthHappyPath() {
        GraphRagHealthResponse downstreamResponse = new GraphRagHealthResponse("ok", "2025-06-12", 1204, true);
        when(graphRagClient.getHealth()).thenReturn(downstreamResponse);

        HealthResponse response = healthController.getHealth();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("ok");
        assertThat(response.isSemanticServiceReachable()).isTrue();
        assertThat(response.isGraphServiceReachable()).isTrue();
    }

    @Test
    void testGetHealthDegradedNeo4j() {
        GraphRagHealthResponse downstreamResponse = new GraphRagHealthResponse("ok", "2025-06-12", 1204, false);
        when(graphRagClient.getHealth()).thenReturn(downstreamResponse);

        HealthResponse response = healthController.getHealth();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("degraded");
        assertThat(response.isSemanticServiceReachable()).isTrue();
        assertThat(response.isGraphServiceReachable()).isFalse();
    }

    @Test
    void testGetHealthDegradedDownstream() {
        GraphRagHealthResponse downstreamResponse = new GraphRagHealthResponse("degraded", null, 0, false);
        when(graphRagClient.getHealth()).thenReturn(downstreamResponse);

        HealthResponse response = healthController.getHealth();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("degraded");
        assertThat(response.isSemanticServiceReachable()).isFalse();
        assertThat(response.isGraphServiceReachable()).isFalse();
    }
}
