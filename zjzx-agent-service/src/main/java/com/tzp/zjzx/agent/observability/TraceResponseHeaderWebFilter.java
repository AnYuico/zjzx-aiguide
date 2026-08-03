package com.tzp.zjzx.agent.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceResponseHeaderWebFilter implements WebFilter {

    static final String TRACE_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    public TraceResponseHeaderWebFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracer = tracerProvider.getIfAvailable();
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            Span span = resolveSpan(exchange);
            if (span != null) {
                    exchange.getResponse().getHeaders().set(
                            TRACE_HEADER,
                            span.context().traceId()
                    );
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    private Span resolveSpan(ServerWebExchange exchange) {
        ServerRequestObservationContext observationContext =
                ServerRequestObservationContext
                        .findCurrent(exchange.getAttributes())
                        .orElse(null);
        if (observationContext != null) {
            TracingObservationHandler.TracingContext tracingContext =
                    observationContext.get(
                            TracingObservationHandler.TracingContext.class
                    );
            if (tracingContext != null && tracingContext.getSpan() != null) {
                return tracingContext.getSpan();
            }
        }
        return tracer == null ? null : tracer.currentSpan();
    }
}
