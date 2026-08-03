package com.tzp.zjzx.agent.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tzp.zjzx.agent.config.AgentMcpProperties;
import org.reactivestreams.Publisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Set;

@Component
@ConditionalOnProperty(
        prefix = "zjzx.agent.mcp",
        name = "enabled",
        havingValue = "true"
)
public class McpCapabilityGuardWebFilter implements WebFilter, Ordered {

    private static final Set<String> ALLOWED_METHODS = Set.of(
            "initialize",
            "notifications/initialized",
            "tools/list",
            "tools/call"
    );
    private static final byte[] EMPTY_BODY = new byte[0];

    private final AgentMcpProperties properties;
    private final ObjectMapper objectMapper;

    public McpCapabilityGuardWebFilter(
            AgentMcpProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(this::readAndRelease)
                .defaultIfEmpty(EMPTY_BODY)
                .flatMap(body -> guard(exchange, chain, body));
    }

    private Mono<Void> guard(
            ServerWebExchange exchange,
            WebFilterChain chain,
            byte[] body) {
        if (body.length == 0) {
            return chain.filter(exchange);
        }

        JsonNode request;
        try {
            request = objectMapper.readTree(body);
        } catch (IOException exception) {
            return replay(exchange, chain, body);
        }

        JsonNode method = request == null ? null : request.get("method");
        if (method == null
                || !method.isTextual()
                || ALLOWED_METHODS.contains(method.textValue())) {
            return replay(exchange, chain, body);
        }

        return writeMethodNotFound(exchange, request.get("id"));
    }

    private byte[] readAndRelease(DataBuffer buffer) {
        try {
            byte[] body = new byte[buffer.readableByteCount()];
            buffer.read(body);
            return body;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private Mono<Void> replay(
            ServerWebExchange exchange,
            WebFilterChain chain,
            byte[] body) {
        ServerHttpRequest request = new ServerHttpRequestDecorator(
                exchange.getRequest()
        ) {
            @Override
            public Flux<DataBuffer> getBody() {
                DataBuffer replayed = exchange.getResponse()
                        .bufferFactory()
                        .wrap(body);
                return Flux.just(replayed);
            }
        };
        return chain.filter(exchange.mutate().request(request).build());
    }

    private Mono<Void> writeMethodNotFound(
            ServerWebExchange exchange,
            JsonNode requestId) {
        ObjectNode responseBody = objectMapper.createObjectNode();
        responseBody.put("jsonrpc", "2.0");
        if (requestId == null) {
            responseBody.putNull("id");
        } else {
            responseBody.set("id", requestId);
        }
        ObjectNode error = responseBody.putObject("error");
        error.put("code", -32601);
        error.put("message", "Method not found");

        byte[] responseBytes;
        try {
            responseBytes = objectMapper.writeValueAsBytes(responseBody);
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }

        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders()
                .setContentLength(responseBytes.length);
        Publisher<DataBuffer> output = Mono.just(
                exchange.getResponse()
                        .bufferFactory()
                        .wrap(responseBytes)
        );
        return exchange.getResponse().writeWith(output);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }
}
