package com.tzp.zjzx.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Component
public class InternalApiSecurityGlobalFilter implements GlobalFilter, Ordered {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isInternalPath(path)) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(INTERNAL_TOKEN_HEADER))
                .build();
        return chain.filter(exchange.mutate().request(sanitizedRequest).build());
    }

    static boolean isInternalPath(String path) {
        return path != null && Arrays.stream(path.split("/"))
                .map(InternalApiSecurityGlobalFilter::removeMatrixParameters)
                .anyMatch(segment -> "internal".equalsIgnoreCase(segment));
    }

    private static String removeMatrixParameters(String segment) {
        int parameterIndex = segment.indexOf(';');
        return parameterIndex < 0 ? segment : segment.substring(0, parameterIndex);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
