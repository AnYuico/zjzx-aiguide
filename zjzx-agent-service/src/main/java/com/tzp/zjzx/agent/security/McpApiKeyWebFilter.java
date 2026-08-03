package com.tzp.zjzx.agent.security;

import com.tzp.zjzx.agent.config.AgentMcpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@ConditionalOnProperty(
        prefix = "zjzx.agent.mcp",
        name = "enabled",
        havingValue = "true"
)
public class McpApiKeyWebFilter implements WebFilter, Ordered {

    public static final String API_KEY_HEADER = "X-MCP-API-Key";

    private final AgentMcpProperties properties;

    public McpApiKeyWebFilter(AgentMcpProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {
        String requestPath = exchange.getRequest()
                .getPath()
                .pathWithinApplication()
                .value();
        if (!properties.getEndpoint().equals(requestPath)) {
            return chain.filter(exchange);
        }

        String suppliedKey = exchange.getRequest()
                .getHeaders()
                .getFirst(API_KEY_HEADER);
        if (matches(suppliedKey)) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean matches(String suppliedKey) {
        byte[] expected = properties.getApiKey()
                .getBytes(StandardCharsets.UTF_8);
        byte[] actual = suppliedKey == null
                ? new byte[0]
                : suppliedKey.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
