package com.company.sage.chat;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.model.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final GraphRagClient graphRagClient;

    public HealthController(GraphRagClient graphRagClient) {
        this.graphRagClient = graphRagClient;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        boolean reachable = graphRagClient.checkHealth();
        String status = reachable ? "ok" : "degraded";
        return new HealthResponse(status, reachable, reachable);
    }
}
