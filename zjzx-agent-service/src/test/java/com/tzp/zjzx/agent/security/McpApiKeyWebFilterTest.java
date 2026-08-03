package com.tzp.zjzx.agent.security;

import com.tzp.zjzx.agent.config.AgentMcpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpApiKeyWebFilterTest {

    private static final String TEST_KEY =
            "test-key-with-at-least-32-characters";

    private McpApiKeyWebFilter filter;

    @BeforeEach
    void setUp() {
        AgentMcpProperties properties = new AgentMcpProperties();
        properties.setEnabled(true);
        properties.setEndpoint("/mcp");
        properties.setApiKey(TEST_KEY);
        properties.afterPropertiesSet();
        filter = new McpApiKeyWebFilter(properties);
    }

    @Test
    void rejectsMcpRequestWithoutApiKey() {
        AtomicBoolean called = new AtomicBoolean();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/mcp").build()
        );

        filter.filter(exchange, ignored -> {
            called.set(true);
            return Mono.empty();
        }).block();

        assertFalse(called.get());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange.getResponse().getStatusCode()
        );
    }

    @Test
    void allowsMcpRequestWithCorrectApiKey() {
        AtomicBoolean called = new AtomicBoolean();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/mcp")
                        .header(McpApiKeyWebFilter.API_KEY_HEADER, TEST_KEY)
                        .build()
        );

        filter.filter(exchange, ignored -> {
            called.set(true);
            return Mono.empty();
        }).block();

        assertTrue(called.get());
    }

    @Test
    void doesNotProtectUnrelatedAgentEndpoint() {
        AtomicBoolean called = new AtomicBoolean();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build()
        );

        filter.filter(exchange, ignored -> {
            called.set(true);
            return Mono.empty();
        }).block();

        assertTrue(called.get());
    }
}
