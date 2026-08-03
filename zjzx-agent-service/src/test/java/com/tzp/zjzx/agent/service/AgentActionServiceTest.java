package com.tzp.zjzx.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.agent.action.AgentActionRecord;
import com.tzp.zjzx.agent.action.AgentActionStatus;
import com.tzp.zjzx.agent.action.AgentActionType;
import com.tzp.zjzx.agent.client.PersonalDataClient;
import com.tzp.zjzx.agent.client.ProductGuideCatalogClient;
import com.tzp.zjzx.agent.config.PersonalToolsProperties;
import com.tzp.zjzx.agent.exception.AgentActionConflictException;
import com.tzp.zjzx.agent.exception.AgentActionNotFoundException;
import com.tzp.zjzx.agent.exception.PersonalActionRejectedException;
import com.tzp.zjzx.agent.exception.PersonalDataUnavailableException;
import com.tzp.zjzx.agent.repository.AgentActionRepository;
import com.tzp.zjzx.ai.contract.dto.AgentOrderCancellationCandidateDto;
import com.tzp.zjzx.ai.contract.vo.AgentActionPreparationVo;
import com.tzp.zjzx.ai.contract.vo.AgentCartMutationResultVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderCancellationResultVo;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentActionServiceTest {

    private final InMemoryAgentActionRepository repository =
            new InMemoryAgentActionRepository();
    private final ProductGuideCatalogClient productCatalogClient =
            mock(ProductGuideCatalogClient.class);
    private final PersonalDataClient personalDataClient =
            mock(PersonalDataClient.class);
    private AgentActionService actionService;

    @BeforeEach
    void setUp() {
        PersonalToolsProperties properties = new PersonalToolsProperties();
        properties.setEnabled(true);
        properties.setActionsEnabled(true);
        properties.setInternalToken("internal-secret");
        properties.setConfirmationTtl(Duration.ofMinutes(5));
        properties.setExecutionLease(Duration.ofSeconds(30));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setMaxCartQuantity(10);
        actionService = new AgentActionService(
                repository,
                productCatalogClient,
                personalDataClient,
                properties,
                new ObjectMapper()
        );
        when(productCatalogClient.getBySkuId(14L))
                .thenReturn(Mono.just(availableProduct()));
    }

    @Test
    void preparePersistsPendingActionWithoutMutatingCart() {
        AgentActionPreparationVo preparation =
                actionService.prepareAddToCart(33L, 14L, 2);

        AgentActionRecord stored = repository.current(
                preparation.getConfirmationId()
        );
        assertEquals(AgentActionStatus.PENDING, stored.status());
        assertEquals(33L, stored.userId());
        assertTrue(preparation.getRequiresConfirmation());
        verify(personalDataClient, never()).addCartItem(
                eq(33L),
                anyString(),
                eq(14L),
                eq(2)
        );
    }

    @Test
    void repeatedConfirmationMutatesCartOnlyOnce() {
        AgentActionPreparationVo preparation =
                actionService.prepareAddToCart(33L, 14L, 2);
        when(personalDataClient.addCartItem(
                33L,
                preparation.getConfirmationId(),
                14L,
                2
        )).thenReturn(Mono.just(mutation(false)));

        StepVerifier.create(actionService.confirm(
                        33L,
                        preparation.getConfirmationId(),
                        true
                ))
                .assertNext(result -> {
                    assertEquals("SUCCEEDED", result.getStatus());
                    assertFalse(result.getReplayed());
                })
                .verifyComplete();
        StepVerifier.create(actionService.confirm(
                        33L,
                        preparation.getConfirmationId(),
                        true
                ))
                .assertNext(result -> {
                    assertEquals("SUCCEEDED", result.getStatus());
                    assertTrue(result.getReplayed());
                })
                .verifyComplete();

        verify(personalDataClient).addCartItem(
                33L,
                preparation.getConfirmationId(),
                14L,
                2
        );
    }

    @Test
    void failedDependencyCanRetryWithSameConfirmationId() {
        AgentActionPreparationVo preparation =
                actionService.prepareAddToCart(33L, 14L, 2);
        when(personalDataClient.addCartItem(
                33L,
                preparation.getConfirmationId(),
                14L,
                2
        ))
                .thenReturn(Mono.error(new PersonalDataUnavailableException(
                        "Cart service unavailable"
                )))
                .thenReturn(Mono.just(mutation(true)));

        StepVerifier.create(actionService.confirm(
                        33L,
                        preparation.getConfirmationId(),
                        true
                ))
                .expectError(PersonalDataUnavailableException.class)
                .verify();
        assertEquals(
                AgentActionStatus.FAILED_RETRYABLE,
                repository.current(preparation.getConfirmationId()).status()
        );

        StepVerifier.create(actionService.confirm(
                        33L,
                        preparation.getConfirmationId(),
                        true
                ))
                .assertNext(result -> {
                    assertEquals("SUCCEEDED", result.getStatus());
                    assertTrue(result.getReplayed());
                })
                .verifyComplete();
        assertEquals(
                2,
                repository.current(
                        preparation.getConfirmationId()
                ).attemptCount()
        );
    }

    @Test
    void anotherUserCannotConfirmAction() {
        AgentActionPreparationVo preparation =
                actionService.prepareAddToCart(33L, 14L, 1);

        StepVerifier.create(actionService.confirm(
                        34L,
                        preparation.getConfirmationId(),
                        true
                ))
                .expectError(AgentActionNotFoundException.class)
                .verify();
        verify(personalDataClient, never()).addCartItem(
                eq(34L),
                anyString(),
                eq(14L),
                eq(1)
        );
    }

    @Test
    void concurrentConfirmationsHaveOneWinner() {
        AgentActionPreparationVo preparation =
                actionService.prepareAddToCart(33L, 14L, 1);
        when(personalDataClient.addCartItem(
                33L,
                preparation.getConfirmationId(),
                14L,
                1
        )).thenReturn(Mono.delay(Duration.ofMillis(100))
                .map(ignored -> mutation(false)));

        Mono<Signal<com.tzp.zjzx.ai.contract.vo.AgentActionConfirmationVo>>
                first = actionService.confirm(
                33L,
                preparation.getConfirmationId(),
                true
        ).materialize();
        Mono<Signal<com.tzp.zjzx.ai.contract.vo.AgentActionConfirmationVo>>
                second = actionService.confirm(
                33L,
                preparation.getConfirmationId(),
                true
        ).materialize();

        StepVerifier.create(Mono.zip(first, second))
                .assertNext(signals -> {
                    Signal<?> firstSignal = signals.getT1();
                    Signal<?> secondSignal = signals.getT2();
                    int successful = (firstSignal.isOnNext() ? 1 : 0)
                            + (secondSignal.isOnNext() ? 1 : 0);
                    Throwable failure = firstSignal.isOnError()
                            ? firstSignal.getThrowable()
                            : secondSignal.getThrowable();
                    assertEquals(1, successful);
                    assertInstanceOf(
                            AgentActionConflictException.class,
                            failure
                    );
                })
                .verifyComplete();
        verify(personalDataClient).addCartItem(
                33L,
                preparation.getConfirmationId(),
                14L,
                1
        );
    }

    @Test
    void prepareCancellationStoresOrderNumberOnlyInInternalPayload() {
        when(personalDataClient.getCancellationCandidate(33L, 1))
                .thenReturn(Mono.just(cancellationCandidate()));

        AgentActionPreparationVo preparation =
                actionService.prepareCancelRecentOrder(33L, 1);

        AgentActionRecord stored =
                repository.current(preparation.getConfirmationId());
        assertEquals(
                AgentActionType.CANCEL_RECENT_ORDER,
                stored.actionType()
        );
        assertTrue(stored.payloadJson().contains("internal-order-61"));
        assertFalse(preparation.getSummary().contains("internal-order-61"));
        assertEquals(
                "CANCEL_RECENT_ORDER",
                preparation.getActionType()
        );
        verify(personalDataClient, never()).cancelOrder(
                eq(33L),
                anyString(),
                anyString()
        );
    }

    @Test
    void repeatedCancellationConfirmationCallsOrderServiceOnce() {
        when(personalDataClient.getCancellationCandidate(33L, 1))
                .thenReturn(Mono.just(cancellationCandidate()));
        AgentActionPreparationVo preparation =
                actionService.prepareCancelRecentOrder(33L, 1);
        when(personalDataClient.cancelOrder(
                33L,
                preparation.getConfirmationId(),
                "internal-order-61"
        )).thenReturn(Mono.just(cancellation(false)));

        StepVerifier.create(actionService.confirm(
                        33L,
                        preparation.getConfirmationId(),
                        true
                ))
                .assertNext(result -> {
                    assertEquals("SUCCEEDED", result.getStatus());
                    assertEquals("待付款订单已取消", result.getMessage());
                    assertFalse(result.getReplayed());
                })
                .verifyComplete();
        StepVerifier.create(actionService.confirm(
                        33L,
                        preparation.getConfirmationId(),
                        true
                ))
                .assertNext(result -> assertTrue(result.getReplayed()))
                .verifyComplete();

        verify(personalDataClient).cancelOrder(
                33L,
                preparation.getConfirmationId(),
                "internal-order-61"
        );
    }

    @Test
    void lostCancellationResponseRetriesWithOriginalConfirmationId() {
        when(personalDataClient.getCancellationCandidate(33L, 1))
                .thenReturn(Mono.just(cancellationCandidate()));
        AgentActionPreparationVo preparation =
                actionService.prepareCancelRecentOrder(33L, 1);
        when(personalDataClient.cancelOrder(
                33L,
                preparation.getConfirmationId(),
                "internal-order-61"
        ))
                .thenReturn(Mono.error(new PersonalDataUnavailableException(
                        "Order service response was lost"
                )))
                .thenReturn(Mono.just(cancellation(true)));

        StepVerifier.create(actionService.confirm(
                        33L,
                        preparation.getConfirmationId(),
                        true
                ))
                .expectError(PersonalDataUnavailableException.class)
                .verify();
        assertEquals(
                AgentActionStatus.FAILED_RETRYABLE,
                repository.current(preparation.getConfirmationId()).status()
        );

        StepVerifier.create(actionService.confirm(
                        33L,
                        preparation.getConfirmationId(),
                        true
                ))
                .assertNext(result -> {
                    assertEquals("SUCCEEDED", result.getStatus());
                    assertTrue(result.getReplayed());
                })
                .verifyComplete();
        verify(personalDataClient, times(2)).cancelOrder(
                33L,
                preparation.getConfirmationId(),
                "internal-order-61"
        );
    }

    @Test
    void paidOrderBeforeConfirmationPermanentlyRejectsAction() {
        when(personalDataClient.getCancellationCandidate(33L, 1))
                .thenReturn(Mono.just(cancellationCandidate()));
        AgentActionPreparationVo preparation =
                actionService.prepareCancelRecentOrder(33L, 1);
        when(personalDataClient.cancelOrder(
                33L,
                preparation.getConfirmationId(),
                "internal-order-61"
        )).thenReturn(Mono.error(new PersonalActionRejectedException(
                "Order is no longer waiting for payment"
        )));

        StepVerifier.create(actionService.confirm(
                        33L,
                        preparation.getConfirmationId(),
                        true
                ))
                .expectError(AgentActionConflictException.class)
                .verify();
        assertEquals(
                AgentActionStatus.REJECTED,
                repository.current(preparation.getConfirmationId()).status()
        );
    }

    private ProductGuideVo availableProduct() {
        ProductGuideVo product = new ProductGuideVo();
        product.setSkuId(14L);
        product.setProductName("Mac mini");
        product.setSkuName("Mac mini 16G");
        product.setSalePrice(new BigDecimal("1999.00"));
        product.setThumbImg("http://image.test/mac.png");
        product.setInStock(true);
        return product;
    }

    private AgentCartMutationResultVo mutation(boolean replayed) {
        AgentCartMutationResultVo result = new AgentCartMutationResultVo();
        result.setApplied(!replayed);
        result.setReplayed(replayed);
        return result;
    }

    private AgentOrderCancellationCandidateDto cancellationCandidate() {
        AgentOrderCancellationCandidateDto candidate =
                new AgentOrderCancellationCandidateDto();
        candidate.setRecentPosition(1);
        candidate.setOrderNo("internal-order-61");
        candidate.setTotalAmount(new BigDecimal("1999.00"));
        candidate.setCreatedAt("2026-07-29 18:00:00");
        candidate.setProductNames(List.of("Mac mini 16G"));
        return candidate;
    }

    private AgentOrderCancellationResultVo cancellation(boolean replayed) {
        AgentOrderCancellationResultVo result =
                new AgentOrderCancellationResultVo();
        result.setApplied(!replayed);
        result.setReplayed(replayed);
        return result;
    }

    private static final class InMemoryAgentActionRepository
            implements AgentActionRepository {

        private final Map<String, AgentActionRecord> actions =
                new ConcurrentHashMap<>();

        @Override
        public synchronized void create(AgentActionRecord action) {
            if (actions.putIfAbsent(action.confirmationId(), action) != null) {
                throw new IllegalStateException("Duplicate confirmation ID");
            }
        }

        @Override
        public Optional<AgentActionRecord> findForUser(
                String confirmationId,
                Long userId) {
            return Optional.ofNullable(actions.get(confirmationId))
                    .filter(action -> action.userId().equals(userId));
        }

        @Override
        public synchronized boolean claimExecution(
                String confirmationId,
                Long userId,
                Instant now,
                Instant staleBefore) {
            AgentActionRecord action = matching(confirmationId, userId);
            if (action == null || !action.expiresAt().isAfter(now)) {
                return false;
            }
            boolean claimable = action.status() == AgentActionStatus.PENDING
                    || action.status() == AgentActionStatus.FAILED_RETRYABLE
                    || (action.status() == AgentActionStatus.EXECUTING
                    && action.executionStartedAt() != null
                    && action.executionStartedAt().isBefore(staleBefore));
            if (!claimable) {
                return false;
            }
            actions.put(confirmationId, copy(
                    action,
                    AgentActionStatus.EXECUTING,
                    now,
                    action.attemptCount() + 1,
                    null,
                    null
            ));
            return true;
        }

        @Override
        public synchronized boolean markSucceeded(
                String confirmationId,
                Long userId,
                String resultJson) {
            return transition(
                    confirmationId,
                    userId,
                    AgentActionStatus.EXECUTING,
                    AgentActionStatus.SUCCEEDED,
                    resultJson,
                    null
            );
        }

        @Override
        public synchronized boolean markRetryable(
                String confirmationId,
                Long userId,
                String lastError) {
            return transition(
                    confirmationId,
                    userId,
                    AgentActionStatus.EXECUTING,
                    AgentActionStatus.FAILED_RETRYABLE,
                    null,
                    lastError
            );
        }

        @Override
        public synchronized boolean rejectPending(
                String confirmationId,
                Long userId,
                String reason) {
            AgentActionRecord action = matching(confirmationId, userId);
            if (action == null
                    || (action.status() != AgentActionStatus.PENDING
                    && action.status() != AgentActionStatus.FAILED_RETRYABLE)) {
                return false;
            }
            actions.put(confirmationId, copy(
                    action,
                    AgentActionStatus.REJECTED,
                    action.executionStartedAt(),
                    action.attemptCount(),
                    null,
                    reason
            ));
            return true;
        }

        @Override
        public synchronized boolean markExecutionRejected(
                String confirmationId,
                Long userId,
                String reason) {
            return transition(
                    confirmationId,
                    userId,
                    AgentActionStatus.EXECUTING,
                    AgentActionStatus.REJECTED,
                    null,
                    reason
            );
        }

        @Override
        public synchronized boolean expire(
                String confirmationId,
                Long userId,
                Instant now) {
            AgentActionRecord action = matching(confirmationId, userId);
            if (action == null || action.expiresAt().isAfter(now)) {
                return false;
            }
            actions.put(confirmationId, copy(
                    action,
                    AgentActionStatus.EXPIRED,
                    action.executionStartedAt(),
                    action.attemptCount(),
                    action.resultJson(),
                    action.lastError()
            ));
            return true;
        }

        private synchronized AgentActionRecord current(String confirmationId) {
            return actions.get(confirmationId);
        }

        private AgentActionRecord matching(
                String confirmationId,
                Long userId) {
            AgentActionRecord action = actions.get(confirmationId);
            return action != null && action.userId().equals(userId)
                    ? action
                    : null;
        }

        private boolean transition(
                String confirmationId,
                Long userId,
                AgentActionStatus expected,
                AgentActionStatus target,
                String resultJson,
                String lastError) {
            AgentActionRecord action = matching(confirmationId, userId);
            if (action == null || action.status() != expected) {
                return false;
            }
            actions.put(confirmationId, copy(
                    action,
                    target,
                    action.executionStartedAt(),
                    action.attemptCount(),
                    resultJson,
                    lastError
            ));
            return true;
        }

        private AgentActionRecord copy(
                AgentActionRecord action,
                AgentActionStatus status,
                Instant executionStartedAt,
                int attemptCount,
                String resultJson,
                String lastError) {
            return new AgentActionRecord(
                    action.confirmationId(),
                    action.userId(),
                    action.actionType(),
                    action.payloadJson(),
                    action.payloadHash(),
                    action.summary(),
                    status,
                    action.expiresAt(),
                    executionStartedAt,
                    attemptCount,
                    resultJson,
                    lastError,
                    action.createdAt(),
                    Instant.now()
            );
        }
    }
}
