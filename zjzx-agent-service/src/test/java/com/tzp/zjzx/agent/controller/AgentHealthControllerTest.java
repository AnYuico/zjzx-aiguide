package com.tzp.zjzx.agent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(AgentHealthController.class)
class AgentHealthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void returnsAgentHealthWithoutCommerceDependencies() {
        webTestClient.get()
                .uri("/api/agent/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.service").isEqualTo("zjzx-agent-service");
    }
}
