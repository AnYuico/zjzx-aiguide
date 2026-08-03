package com.tzp.zjzx.agent.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResilienceExecutorTest {

    @Test
    void retriesProductReadBeforeRecordingFinalCircuitOutcome() {
        CircuitBreaker productCircuit = CircuitBreaker.of(
                "productCatalog",
                CircuitBreakerConfig.custom()
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(5)
                        .build()
        );
        Retry productRetry = Retry.of(
                "productCatalog",
                RetryConfig.custom()
                        .maxAttempts(2)
                        .waitDuration(Duration.ZERO)
                        .build()
        );
        AgentResilienceExecutor executor = executor(
                productCircuit,
                productRetry,
                Bulkhead.ofDefaults("deepSeek")
        );
        AtomicInteger attempts = new AtomicInteger();

        StepVerifier.create(executor.protectProductCatalog(Mono.defer(() -> {
                    if (attempts.incrementAndGet() == 1) {
                        return Mono.error(new IllegalStateException("temporary"));
                    }
                    return Mono.just("ok");
                })))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(2, attempts.get());
        assertEquals(
                1,
                productCircuit.getMetrics().getNumberOfSuccessfulCalls()
        );
        assertEquals(
                0,
                productCircuit.getMetrics().getNumberOfFailedCalls()
        );
    }

    @Test
    void bulkheadRejectionDoesNotCountAsDeepSeekCircuitFailure() {
        CircuitBreaker deepSeekCircuit =
                CircuitBreaker.ofDefaults("deepSeek");
        Bulkhead bulkhead = Bulkhead.of(
                "deepSeek",
                BulkheadConfig.custom()
                        .maxConcurrentCalls(1)
                        .maxWaitDuration(Duration.ZERO)
                        .build()
        );
        assertTrue(bulkhead.tryAcquirePermission());
        AgentResilienceExecutor executor = new AgentResilienceExecutor(
                CircuitBreaker.ofDefaults("productCatalog"),
                Retry.ofDefaults("productCatalog"),
                deepSeekCircuit,
                bulkhead
        );

        StepVerifier.create(executor.protectDeepSeek(Mono.just("unused")))
                .expectError(BulkheadFullException.class)
                .verify();

        assertEquals(
                0,
                deepSeekCircuit.getMetrics().getNumberOfFailedCalls()
        );
        bulkhead.onComplete();
    }

    private AgentResilienceExecutor executor(
            CircuitBreaker productCircuit,
            Retry productRetry,
            Bulkhead deepSeekBulkhead) {
        return new AgentResilienceExecutor(
                productCircuit,
                productRetry,
                CircuitBreaker.ofDefaults("deepSeek"),
                deepSeekBulkhead
        );
    }
}
