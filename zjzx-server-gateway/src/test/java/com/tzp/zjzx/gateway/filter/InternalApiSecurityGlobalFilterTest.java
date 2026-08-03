package com.tzp.zjzx.gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class InternalApiSecurityGlobalFilterTest {

    private final InternalApiSecurityGlobalFilter filter = new InternalApiSecurityGlobalFilter();

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/product/internal/inventory/reserve",
            "/api/order/orderInfo/internal/markPaid/1/2",
            "/service-product/api/product/internal/inventory/reserve",
            "/api/product/internal;source=external/inventory/reserve"
    })
    void blocksInternalPathsBeforeTheyReachDownstreamServices(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(path).build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.filter(exchange, ignored -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }

    @Test
    void removesInternalTokenHeaderFromPublicRequests() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/product/1/10")
                        .header(InternalApiSecurityGlobalFilter.INTERNAL_TOKEN_HEADER, "forged-token")
                        .build());
        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();

        filter.filter(exchange, sanitizedExchange -> {
            forwardedExchange.set(sanitizedExchange);
            return Mono.empty();
        }).block();

        assertNull(forwardedExchange.get().getRequest().getHeaders()
                .getFirst(InternalApiSecurityGlobalFilter.INTERNAL_TOKEN_HEADER));
    }
}
