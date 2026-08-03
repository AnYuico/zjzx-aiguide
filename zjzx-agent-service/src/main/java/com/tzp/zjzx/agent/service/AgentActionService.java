package com.tzp.zjzx.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.agent.action.AddToCartActionPayload;
import com.tzp.zjzx.agent.action.AgentActionRecord;
import com.tzp.zjzx.agent.action.AgentActionStatus;
import com.tzp.zjzx.agent.action.AgentActionType;
import com.tzp.zjzx.agent.action.CancelRecentOrderActionPayload;
import com.tzp.zjzx.agent.client.PersonalDataClient;
import com.tzp.zjzx.agent.client.ProductGuideCatalogClient;
import com.tzp.zjzx.agent.config.PersonalToolsProperties;
import com.tzp.zjzx.agent.exception.AgentActionConflictException;
import com.tzp.zjzx.agent.exception.AgentActionExpiredException;
import com.tzp.zjzx.agent.exception.AgentActionNotFoundException;
import com.tzp.zjzx.agent.exception.AgentActionUnavailableException;
import com.tzp.zjzx.agent.exception.PersonalActionRejectedException;
import com.tzp.zjzx.agent.exception.PersonalDataUnavailableException;
import com.tzp.zjzx.agent.repository.AgentActionRepository;
import com.tzp.zjzx.ai.contract.dto.AgentOrderCancellationCandidateDto;
import com.tzp.zjzx.ai.contract.vo.AgentActionConfirmationVo;
import com.tzp.zjzx.ai.contract.vo.AgentActionPreparationVo;
import com.tzp.zjzx.ai.contract.vo.AgentCartMutationResultVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderCancellationResultVo;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentActionService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AgentActionService.class);

    private final AgentActionRepository actionRepository;
    private final ProductGuideCatalogClient productCatalogClient;
    private final PersonalDataClient personalDataClient;
    private final PersonalToolsProperties properties;
    private final ObjectMapper objectMapper;

    public AgentActionService(
            AgentActionRepository actionRepository,
            ProductGuideCatalogClient productCatalogClient,
            PersonalDataClient personalDataClient,
            PersonalToolsProperties properties,
            ObjectMapper objectMapper) {
        this.actionRepository = actionRepository;
        this.productCatalogClient = productCatalogClient;
        this.personalDataClient = personalDataClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AgentActionPreparationVo prepareAddToCart(
            Long userId,
            Long skuId,
            Integer quantity) {
        requireActionsEnabled();
        if (userId == null || userId <= 0 || skuId == null || skuId <= 0
                || quantity == null || quantity < 1
                || quantity > properties.getMaxCartQuantity()) {
            throw new IllegalArgumentException(
                    "商品数量必须在 1 到 "
                            + properties.getMaxCartQuantity() + " 之间"
            );
        }

        ProductGuideVo product = productCatalogClient.getBySkuId(skuId)
                .block(properties.getRequestTimeout());
        if (product == null || !skuId.equals(product.getSkuId())
                || !Boolean.TRUE.equals(product.getInStock())
                || product.getSalePrice() == null) {
            throw new AgentActionConflictException("商品当前不可加入购物车");
        }

        String displayName = product.getSkuName() == null
                || product.getSkuName().isBlank()
                ? product.getProductName()
                : product.getSkuName();
        displayName = sanitizeSummaryText(displayName);
        if (displayName.isBlank()) {
            displayName = "SKU " + skuId;
        }
        String summary = truncate(
                "将 " + displayName + " x" + quantity + " 加入购物车",
                500
        );
        AddToCartActionPayload payload = new AddToCartActionPayload(
                skuId,
                quantity,
                product.getProductName(),
                product.getSkuName(),
                product.getSalePrice(),
                product.getThumbImg()
        );
        return preparation(createPendingAction(
                userId,
                AgentActionType.ADD_TO_CART,
                payload,
                summary
        ));
    }

    public AgentActionPreparationVo prepareCancelRecentOrder(
            Long userId,
            Integer recentPosition) {
        requireActionsEnabled();
        if (userId == null || userId <= 0
                || recentPosition == null
                || recentPosition < 1
                || recentPosition > properties.getMaxOrderLimit()) {
            throw new IllegalArgumentException(
                    "待付款订单位置必须在 1 到 "
                            + properties.getMaxOrderLimit() + " 之间"
            );
        }

        AgentOrderCancellationCandidateDto candidate;
        try {
            candidate = personalDataClient.getCancellationCandidate(
                            userId,
                            recentPosition
                    )
                    .block(properties.getRequestTimeout());
        } catch (PersonalActionRejectedException exception) {
            throw new AgentActionConflictException(
                    "对应的待付款订单不存在或状态已经变化"
            );
        }
        if (candidate == null
                || !recentPosition.equals(candidate.getRecentPosition())
                || candidate.getOrderNo() == null
                || candidate.getOrderNo().isBlank()
                || candidate.getOrderNo().length() > 64) {
            throw new AgentActionUnavailableException(
                    "Order service returned an invalid cancellation candidate"
            );
        }

        List<String> productNames = candidate.getProductNames() == null
                ? List.of()
                : candidate.getProductNames().stream()
                .map(this::sanitizeSummaryText)
                .filter(name -> !name.isBlank())
                .limit(3)
                .toList();
        String productSummary = productNames.isEmpty()
                ? "待付款订单"
                : String.join("、", productNames);
        String amountSummary = candidate.getTotalAmount() == null
                ? ""
                : "，金额 ¥" + candidate.getTotalAmount().toPlainString();
        String summary = truncate(
                "取消第 " + recentPosition + " 个近期待付款订单："
                        + productSummary + amountSummary,
                500
        );
        CancelRecentOrderActionPayload payload =
                new CancelRecentOrderActionPayload(
                        candidate.getOrderNo(),
                        recentPosition,
                        candidate.getTotalAmount(),
                        candidate.getCreatedAt(),
                        productNames
                );
        return preparation(createPendingAction(
                userId,
                AgentActionType.CANCEL_RECENT_ORDER,
                payload,
                summary
        ));
    }

    private AgentActionRecord createPendingAction(
            Long userId,
            AgentActionType actionType,
            Object payload,
            String summary) {
        String confirmationId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(properties.getConfirmationTtl());
        String payloadJson = writeJson(payload);
        AgentActionRecord action = new AgentActionRecord(
                confirmationId,
                userId,
                actionType,
                payloadJson,
                sha256(payloadJson),
                summary,
                AgentActionStatus.PENDING,
                expiresAt,
                null,
                0,
                null,
                null,
                null,
                null
        );
        try {
            actionRepository.create(action);
        } catch (DataAccessException exception) {
            throw new AgentActionUnavailableException(
                    "Unable to persist action confirmation",
                    exception
            );
        }
        return action;
    }

    public Mono<AgentActionConfirmationVo> confirm(
            Long userId,
            String confirmationId,
            Boolean confirmed) {
        requireActionsEnabled();
        validateConfirmationRequest(userId, confirmationId, confirmed);
        return Mono.fromCallable(() -> loadForUser(confirmationId, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(action -> Boolean.TRUE.equals(confirmed)
                        ? executeConfirmed(action)
                        : rejectConfirmation(action));
    }

    private Mono<AgentActionConfirmationVo> executeConfirmed(
            AgentActionRecord action) {
        validatePayloadIntegrity(action);
        Instant now = Instant.now();
        if (action.status() == AgentActionStatus.SUCCEEDED) {
            return Mono.just(successResult(action, true));
        }
        if (action.status() == AgentActionStatus.REJECTED) {
            return Mono.error(new AgentActionConflictException(
                    "确认操作已被拒绝"
            ));
        }
        Instant staleBefore = now.minus(properties.getExecutionLease());
        if (action.status() == AgentActionStatus.EXECUTING
                && action.executionStartedAt() != null
                && action.executionStartedAt().isAfter(staleBefore)) {
            return Mono.error(new AgentActionConflictException(
                    "确认操作正在执行"
            ));
        }
        if (action.status() == AgentActionStatus.EXPIRED
                || !action.expiresAt().isAfter(now)) {
            expireQuietly(action, now);
            return Mono.error(new AgentActionExpiredException());
        }

        boolean claimed;
        try {
            claimed = actionRepository.claimExecution(
                    action.confirmationId(),
                    action.userId(),
                    now,
                    staleBefore
            );
        } catch (DataAccessException exception) {
            return Mono.error(new AgentActionUnavailableException(
                    "Unable to claim action confirmation",
                    exception
            ));
        }
        if (!claimed) {
            return resolveClaimMiss(action.confirmationId(), action.userId());
        }

        return executeBusinessAction(action)
                .onErrorResume(PersonalActionRejectedException.class,
                        failure -> rejectDownstream(action, failure))
                .onErrorResume(PersonalDataUnavailableException.class,
                        failure -> retryableDownstream(action, failure));
    }

    private Mono<AgentActionConfirmationVo> executeBusinessAction(
            AgentActionRecord action) {
        return switch (action.actionType()) {
            case ADD_TO_CART -> {
                AddToCartActionPayload payload =
                        readCartPayload(action.payloadJson());
                yield personalDataClient.addCartItem(
                                action.userId(),
                                action.confirmationId(),
                                payload.skuId(),
                                payload.quantity()
                        )
                        .flatMap(result -> completeCartAction(action, result));
            }
            case CANCEL_RECENT_ORDER -> {
                CancelRecentOrderActionPayload payload =
                        readCancelOrderPayload(action.payloadJson());
                yield personalDataClient.cancelOrder(
                                action.userId(),
                                action.confirmationId(),
                                payload.orderNo()
                        )
                        .flatMap(result ->
                                completeOrderCancellation(action, result));
            }
        };
    }

    private Mono<AgentActionConfirmationVo> rejectConfirmation(
            AgentActionRecord action) {
        if (action.status() == AgentActionStatus.SUCCEEDED) {
            return Mono.just(successResult(action, true));
        }
        if (action.status() == AgentActionStatus.EXECUTING) {
            return Mono.error(new AgentActionConflictException(
                    "Action confirmation is already executing"
            ));
        }
        if (action.status() == AgentActionStatus.EXPIRED
                || !action.expiresAt().isAfter(Instant.now())) {
            expireQuietly(action, Instant.now());
            return Mono.error(new AgentActionExpiredException());
        }
        if (action.status() == AgentActionStatus.REJECTED) {
            return Mono.just(rejectedResult(action, true));
        }
        try {
            boolean rejected = actionRepository.rejectPending(
                    action.confirmationId(),
                    action.userId(),
                    "Rejected by user"
            );
            if (rejected) {
                return Mono.just(rejectedResult(action, false));
            }
        } catch (DataAccessException exception) {
            return Mono.error(new AgentActionUnavailableException(
                    "Unable to reject action confirmation",
                    exception
            ));
        }
        return resolveClaimMiss(action.confirmationId(), action.userId());
    }

    private Mono<AgentActionConfirmationVo> completeCartAction(
            AgentActionRecord action,
            AgentCartMutationResultVo mutationResult) {
        if (mutationResult == null
                || Boolean.TRUE.equals(mutationResult.getApplied())
                == Boolean.TRUE.equals(mutationResult.getReplayed())) {
            return Mono.error(
                    new PersonalDataUnavailableException(
                            "Cart service returned an invalid mutation result"
                    )
            );
        }
        return completeAction(
                action,
                "商品已加入购物车",
                Boolean.TRUE.equals(mutationResult.getReplayed())
        );
    }

    private Mono<AgentActionConfirmationVo> completeOrderCancellation(
            AgentActionRecord action,
            AgentOrderCancellationResultVo cancellationResult) {
        if (cancellationResult == null
                || Boolean.TRUE.equals(cancellationResult.getApplied())
                == Boolean.TRUE.equals(cancellationResult.getReplayed())) {
            return Mono.error(
                    new PersonalDataUnavailableException(
                            "Order service returned an invalid cancellation result"
                    )
            );
        }
        return completeAction(
                action,
                "待付款订单已取消",
                Boolean.TRUE.equals(cancellationResult.getReplayed())
        );
    }

    private Mono<AgentActionConfirmationVo> completeAction(
            AgentActionRecord action,
            String message,
            boolean replayed) {
        AgentActionConfirmationVo result = confirmation(
                action,
                AgentActionStatus.SUCCEEDED,
                message,
                replayed
        );
        String resultJson = writeJson(result);
        return Mono.fromCallable(() -> {
                    try {
                        if (actionRepository.markSucceeded(
                                action.confirmationId(),
                                action.userId(),
                                resultJson
                        )) {
                            return result;
                        }
                    } catch (DataAccessException exception) {
                        throw new AgentActionUnavailableException(
                                "Unable to store action result",
                                exception
                        );
                    }
                    AgentActionRecord current = loadForUser(
                            action.confirmationId(),
                            action.userId()
                    );
                    if (current.status() == AgentActionStatus.SUCCEEDED) {
                        return successResult(current, true);
                    }
                    throw new AgentActionUnavailableException(
                            "Action result was not persisted"
                    );
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<AgentActionConfirmationVo> rejectDownstream(
            AgentActionRecord action,
            PersonalActionRejectedException failure) {
        try {
            actionRepository.markExecutionRejected(
                    action.confirmationId(),
                    action.userId(),
                    failure.getMessage()
            );
        } catch (DataAccessException exception) {
            return Mono.error(new AgentActionUnavailableException(
                    "Unable to store rejected action",
                    exception
            ));
        }
        return Mono.error(new AgentActionConflictException(
                "业务操作已被拒绝，请重新发起"
        ));
    }

    private Mono<AgentActionConfirmationVo> retryableDownstream(
            AgentActionRecord action,
            PersonalDataUnavailableException failure) {
        try {
            actionRepository.markRetryable(
                    action.confirmationId(),
                    action.userId(),
                    failure.getClass().getSimpleName()
            );
        } catch (DataAccessException persistenceFailure) {
            LOGGER.warn(
                    "Unable to mark Agent action retryable: confirmationId={}",
                    action.confirmationId()
            );
        }
        return Mono.error(failure);
    }

    private Mono<AgentActionConfirmationVo> resolveClaimMiss(
            String confirmationId,
            Long userId) {
        AgentActionRecord current = loadForUser(confirmationId, userId);
        return switch (current.status()) {
            case SUCCEEDED -> Mono.just(successResult(current, true));
            case EXPIRED -> Mono.error(new AgentActionExpiredException());
            case REJECTED -> Mono.error(new AgentActionConflictException(
                    "确认操作已被拒绝"
            ));
            default -> Mono.error(new AgentActionConflictException(
                    "确认操作正在执行"
            ));
        };
    }

    private AgentActionRecord loadForUser(String confirmationId, Long userId) {
        try {
            Optional<AgentActionRecord> action =
                    actionRepository.findForUser(confirmationId, userId);
            return action.orElseThrow(AgentActionNotFoundException::new);
        } catch (DataAccessException exception) {
            throw new AgentActionUnavailableException(
                    "Unable to load action confirmation",
                    exception
            );
        }
    }

    private void validatePayloadIntegrity(AgentActionRecord action) {
        String canonicalJson = switch (action.actionType()) {
            case ADD_TO_CART ->
                    writeJson(readCartPayload(action.payloadJson()));
            case CANCEL_RECENT_ORDER ->
                    writeJson(readCancelOrderPayload(action.payloadJson()));
        };
        if (!sha256(canonicalJson).equals(action.payloadHash())) {
            throw new AgentActionConflictException(
                    "确认操作内容校验失败"
            );
        }
    }

    private AddToCartActionPayload readCartPayload(String payloadJson) {
        try {
            return objectMapper.readValue(
                    payloadJson,
                    AddToCartActionPayload.class
            );
        } catch (JsonProcessingException exception) {
            throw new AgentActionConflictException(
                    "确认操作内容无法解析"
            );
        }
    }

    private CancelRecentOrderActionPayload readCancelOrderPayload(
            String payloadJson) {
        try {
            return objectMapper.readValue(
                    payloadJson,
                    CancelRecentOrderActionPayload.class
            );
        } catch (JsonProcessingException exception) {
            throw new AgentActionConflictException(
                    "确认操作内容无法解析"
            );
        }
    }

    private AgentActionConfirmationVo successResult(
            AgentActionRecord action,
            boolean replayed) {
        if (action.resultJson() != null) {
            try {
                AgentActionConfirmationVo stored = objectMapper.readValue(
                        action.resultJson(),
                        AgentActionConfirmationVo.class
                );
                stored.setReplayed(replayed);
                return stored;
            } catch (JsonProcessingException ignored) {
                // Rebuild from immutable action data.
            }
        }
        return confirmation(
                action,
                AgentActionStatus.SUCCEEDED,
                successMessage(action.actionType()),
                replayed
        );
    }

    private AgentActionConfirmationVo rejectedResult(
            AgentActionRecord action,
            boolean replayed) {
        return confirmation(
                action,
                AgentActionStatus.REJECTED,
                "操作已取消",
                replayed
        );
    }

    private AgentActionConfirmationVo confirmation(
            AgentActionRecord action,
            AgentActionStatus status,
            String message,
            boolean replayed) {
        AgentActionConfirmationVo result = new AgentActionConfirmationVo();
        result.setConfirmationId(action.confirmationId());
        result.setStatus(status.name());
        result.setSummary(action.summary());
        result.setMessage(message);
        result.setReplayed(replayed);
        return result;
    }

    private AgentActionPreparationVo preparation(AgentActionRecord action) {
        AgentActionPreparationVo result = new AgentActionPreparationVo();
        result.setConfirmationId(action.confirmationId());
        result.setActionType(action.actionType().name());
        result.setSummary(action.summary());
        result.setExpiresAt(
                DateTimeFormatter.ISO_INSTANT.format(action.expiresAt())
        );
        result.setRequiresConfirmation(true);
        return result;
    }

    private void expireQuietly(AgentActionRecord action, Instant now) {
        try {
            actionRepository.expire(
                    action.confirmationId(),
                    action.userId(),
                    now
            );
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Unable to mark Agent action expired: confirmationId={}",
                    action.confirmationId()
            );
        }
    }

    private void validateConfirmationRequest(
            Long userId,
            String confirmationId,
            Boolean confirmed) {
        if (userId == null || userId <= 0 || confirmationId == null
                || confirmed == null) {
            throw new IllegalArgumentException("确认参数不完整");
        }
        try {
            UUID.fromString(confirmationId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("确认编号格式错误");
        }
    }

    private void requireActionsEnabled() {
        if (!properties.isEnabled() || !properties.isActionsEnabled()) {
            throw new AgentActionUnavailableException(
                    "Personal actions are disabled"
            );
        }
    }

    private String successMessage(AgentActionType actionType) {
        return switch (actionType) {
            case ADD_TO_CART -> "商品已加入购物车";
            case CANCEL_RECENT_ORDER -> "待付款订单已取消";
        };
    }

    private String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }

    private String sanitizeSummaryText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value
                .replaceAll("[\\p{Cc}\\p{Cf}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return truncate(normalized, 80);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AgentActionUnavailableException(
                    "Unable to serialize action data",
                    exception
            );
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
