package com.company.sage.chat;

import com.company.sage.clients.graphrag.GraphRagClient;
import com.company.sage.model.GraphRagHealthResponse;
import com.company.sage.model.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the public health check endpoint.
 */
@RestController
public class HealthController {

    private final GraphRagClient graphRagClient;

    public HealthController(GraphRagClient graphRagClient) {
        this.graphRagClient = graphRagClient;
    }

    /**
     * Responds to GET /health requests by aggregating app and downstream reachability.
     */
    @GetMapping("/health")
    public HealthResponse getHealth() {
        GraphRagHealthResponse downstreamHealth = graphRagClient.getHealth();

        boolean semanticReachable = downstreamHealth != null 
                && !"degraded".equals(downstreamHealth.getStatus());
        
        boolean graphReachable = downstreamHealth != null 
                && !"degraded".equals(downstreamHealth.getStatus()) 
                && downstreamHealth.isNeo4jReachable();

        String status = (semanticReachable && graphReachable) ? "ok" : "degraded";

        return new HealthResponse(status, semanticReachable, graphReachable);
    }
}
