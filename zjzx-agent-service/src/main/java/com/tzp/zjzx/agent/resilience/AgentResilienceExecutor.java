package com.tzp.zjzx.agent.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AgentResilienceExecutor {

    private final CircuitBreaker productCatalogCircuitBreaker;
    private final Retry productCatalogRetry;
    private final CircuitBreaker deepSeekCircuitBreaker;
    private final Bulkhead deepSeekBulkhead;

    @Autowired
    public AgentResilienceExecutor(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            BulkheadRegistry bulkheadRegistry) {
        this(
                circuitBreakerRegistry.circuitBreaker("productCatalog"),
                retryRegistry.retry("productCatalog"),
                circuitBreakerRegistry.circuitBreaker("deepSeek"),
                bulkheadRegistry.bulkhead("deepSeek")
        );
    }

    AgentResilienceExecutor(
            CircuitBreaker productCatalogCircuitBreaker,
            Retry productCatalogRetry,
            CircuitBreaker deepSeekCircuitBreaker,
            Bulkhead deepSeekBulkhead) {
        this.productCatalogCircuitBreaker = productCatalogCircuitBreaker;
        this.productCatalogRetry = productCatalogRetry;
        this.deepSeekCircuitBreaker = deepSeekCircuitBreaker;
        this.deepSeekBulkhead = deepSeekBulkhead;
    }

    public <T> Mono<T> protectProductCatalog(Mono<T> operation) {
        return operation
                .transformDeferred(RetryOperator.of(productCatalogRetry))
                .transformDeferred(CircuitBreakerOperator.of(
                        productCatalogCircuitBreaker
                ));
    }

    public <T> Mono<T> protectDeepSeek(Mono<T> operation) {
        return operation
                .transformDeferred(CircuitBreakerOperator.of(
                        deepSeekCircuitBreaker
                ))
                .transformDeferred(BulkheadOperator.of(deepSeekBulkhead));
    }
}
