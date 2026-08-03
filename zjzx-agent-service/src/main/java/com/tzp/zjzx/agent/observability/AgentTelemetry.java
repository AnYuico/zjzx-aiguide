package com.tzp.zjzx.agent.observability;

import com.tzp.zjzx.agent.service.GuideChatResponse;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AgentTelemetry {

    private final MeterRegistry meterRegistry;

    public AgentTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Mono<GuideChatResponse> observeChat(
            Mono<GuideChatResponse> operation) {
        return Mono.defer(() -> {
            long startedAt = System.nanoTime();
            AtomicBoolean recorded = new AtomicBoolean(false);
            return operation
                    .doOnNext(response -> recordChatOnce(
                            recorded,
                            response.mode().name().toLowerCase(),
                            startedAt
                    ))
                    .doOnError(failure -> recordChatOnce(
                            recorded,
                            "error",
                            startedAt
                    ))
                    .doOnCancel(() -> recordChatOnce(
                            recorded,
                            "cancelled",
                            startedAt
                    ));
        });
    }

    public void recordUnsafeOutput() {
        meterRegistry.counter(
                "zjzx.agent.security.output_rejections"
        ).increment();
    }

    public void recordModelFallback(Throwable failure) {
        meterRegistry.counter(
                "zjzx.agent.model.fallbacks",
                "reason",
                modelFailureReason(failure)
        ).increment();
    }

    private String modelFailureReason(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof RestClientResponseException responseException
                    && responseException.getStatusCode().value() == 429) {
                return "upstream_rate_limit";
            }
            if (current instanceof TimeoutException) {
                return "timeout";
            }
            if (current instanceof CallNotPermittedException) {
                return "circuit_open";
            }
            if (current instanceof BulkheadFullException) {
                return "bulkhead_full";
            }
            if (current instanceof ResourceAccessException) {
                return "transport";
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return "model_error";
    }

    private void recordChat(String outcome, long startedAt) {
        meterRegistry.counter(
                "zjzx.agent.chat.requests",
                "outcome",
                outcome
        ).increment();
        Timer.builder("zjzx.agent.chat.duration")
                .description("Shopping guide chat duration")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(
                        System.nanoTime() - startedAt,
                        TimeUnit.NANOSECONDS
                );
    }

    private void recordChatOnce(
            AtomicBoolean recorded,
            String outcome,
            long startedAt) {
        if (recorded.compareAndSet(false, true)) {
            recordChat(outcome, startedAt);
        }
    }
}
