package com.tzp.zjzx.agent.service;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Component
public class ProductKnowledgeIndexCoordinator {

    private final Semaphore semaphore = new Semaphore(1, true);

    public <T> Mono<T> serialize(Supplier<Mono<T>> work) {
        return Mono.usingWhen(
                Mono.fromCallable(this::acquire)
                        .subscribeOn(Schedulers.boundedElastic()),
                ignored -> Mono.defer(work),
                lease -> Mono.fromRunnable(lease::close),
                (lease, failure) -> Mono.fromRunnable(lease::close),
                lease -> Mono.fromRunnable(lease::close)
        );
    }

    public <T> T execute(Callable<T> work) {
        Lease lease = acquire();
        try {
            return work.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Product knowledge index operation failed",
                    exception
            );
        } finally {
            lease.close();
        }
    }

    private Lease acquire() {
        try {
            semaphore.acquire();
            return new Lease(semaphore);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for product index lock",
                    exception
            );
        }
    }

    private static final class Lease implements AutoCloseable {

        private final Semaphore semaphore;
        private boolean released;

        private Lease(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                semaphore.release();
            }
        }
    }
}
