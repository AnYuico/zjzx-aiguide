package com.tzp.zjzx.agent.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceResponseHeaderWebFilterTest {

    @Test
    @SuppressWarnings("unchecked")
    void writesTraceIdFromServerObservationContext() {
        ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
        TraceResponseHeaderWebFilter filter =
                new TraceResponseHeaderWebFilter(tracerProvider);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/agent/health").build()
        );

        Span span = mock(Span.class);
        TraceContext spanContext = mock(TraceContext.class);
        when(span.context()).thenReturn(spanContext);
        when(spanContext.traceId()).thenReturn(
                "0123456789abcdef0123456789abcdef"
        );

        ServerRequestObservationContext observationContext =
                new ServerRequestObservationContext(
                        exchange.getRequest(),
                        exchange.getResponse(),
                        exchange.getAttributes()
                );
        TracingObservationHandler.TracingContext tracingContext =
                new TracingObservationHandler.TracingContext();
        tracingContext.setSpan(span);
        observationContext.put(
                TracingObservationHandler.TracingContext.class,
                tracingContext
        );
        exchange.getAttributes().put(
                ServerRequestObservationContext
                        .CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE,
                observationContext
        );

        StepVerifier.create(filter.filter(
                        exchange,
                        ignored -> exchange.getResponse().setComplete()
                ))
                .verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(
                TraceResponseHeaderWebFilter.TRACE_HEADER
        )).isEqualTo("0123456789abcdef0123456789abcdef");
    }
}
