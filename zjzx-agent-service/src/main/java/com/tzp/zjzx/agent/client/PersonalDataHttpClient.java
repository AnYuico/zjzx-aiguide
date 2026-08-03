package com.tzp.zjzx.agent.client;

import com.tzp.zjzx.agent.config.PersonalToolsProperties;
import com.tzp.zjzx.agent.exception.AgentAuthenticationException;
import com.tzp.zjzx.agent.exception.PersonalActionRejectedException;
import com.tzp.zjzx.agent.exception.PersonalDataUnavailableException;
import com.tzp.zjzx.ai.contract.dto.AgentCartAddRequestDto;
import com.tzp.zjzx.ai.contract.dto.AgentOrderCancelRequestDto;
import com.tzp.zjzx.ai.contract.dto.AgentOrderCancellationCandidateDto;
import com.tzp.zjzx.ai.contract.vo.AgentCartItemVo;
import com.tzp.zjzx.ai.contract.vo.AgentCartMutationResultVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderCancellationResultVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderSummaryVo;
import com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class PersonalDataHttpClient implements PersonalDataClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    static final String MALL_TOKEN_HEADER = "token";
    private static final String PRINCIPAL_PATH =
            "/api/user/userInfo/internal/agent/current";
    private static final String CART_PATH =
            "/api/order/cart/internal/agent/users/{userId}";
    private static final String ORDERS_PATH =
            "/api/order/orderInfo/internal/agent/users/{userId}/recent";
    private static final String CART_ITEMS_PATH =
            "/api/order/cart/internal/agent/users/{userId}/items";
    private static final String ORDER_CANCELLATION_CANDIDATE_PATH =
            "/api/order/orderInfo/internal/agent/users/{userId}"
                    + "/cancellation-candidates/{recentPosition}";
    private static final String ORDER_CANCELLATIONS_PATH =
            "/api/order/orderInfo/internal/agent/users/{userId}/cancellations";

    private final WebClient userClient;
    private final WebClient cartClient;
    private final WebClient orderClient;
    private final String internalToken;
    private final Duration requestTimeout;

    @Autowired
    public PersonalDataHttpClient(WebClient.Builder builder,
                                  PersonalToolsProperties properties) {
        this(
                builder.clone().baseUrl(properties.getUserServiceBaseUrl()).build(),
                builder.clone().baseUrl(properties.getCartServiceBaseUrl()).build(),
                builder.clone().baseUrl(properties.getOrderServiceBaseUrl()).build(),
                properties.getInternalToken(),
                properties.getRequestTimeout()
        );
    }

    PersonalDataHttpClient(WebClient userClient,
                           WebClient cartClient,
                           WebClient orderClient,
                           String internalToken,
                           Duration requestTimeout) {
        this.userClient = userClient;
        this.cartClient = cartClient;
        this.orderClient = orderClient;
        this.internalToken = internalToken;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public Mono<AgentUserPrincipalVo> resolvePrincipal(String mallToken) {
        if (!StringUtils.hasText(mallToken)) {
            return Mono.error(new AgentAuthenticationException());
        }
        return protectedCall(Mono.defer(() -> {
            verifyConfiguration();
            return userClient.get()
                    .uri(PRINCIPAL_PATH)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .header(MALL_TOKEN_HEADER, mallToken)
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.UNAUTHORIZED.value(),
                            response -> Mono.error(new AgentAuthenticationException()))
                    .onStatus(HttpStatusCode::isError, response -> upstreamError(
                            "User service",
                            response.statusCode()
                    ))
                    .bodyToMono(AgentUserPrincipalVo.class)
                    .switchIfEmpty(Mono.error(new AgentAuthenticationException()));
        }), "User service");
    }

    @Override
    public Mono<List<AgentCartItemVo>> getCart(Long userId) {
        requireUserId(userId);
        return protectedCall(Mono.defer(() -> {
            verifyConfiguration();
            return cartClient.get()
                    .uri(CART_PATH, userId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> upstreamError(
                            "Cart service",
                            response.statusCode()
                    ))
                    .bodyToMono(new ParameterizedTypeReference<List<AgentCartItemVo>>() {
                    });
        }), "Cart service").map(items -> items == null ? List.of() : List.copyOf(items));
    }

    @Override
    public Mono<List<AgentOrderSummaryVo>> listRecentOrders(
            Long userId,
            String status,
            int limit) {
        requireUserId(userId);
        return protectedCall(Mono.defer(() -> {
            verifyConfiguration();
            return orderClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(ORDERS_PATH)
                            .queryParam("status", status)
                            .queryParam("limit", limit)
                            .build(userId))
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> upstreamError(
                            "Order service",
                            response.statusCode()
                    ))
                    .bodyToMono(new ParameterizedTypeReference<
                            List<AgentOrderSummaryVo>>() {
                    });
        }), "Order service").map(orders ->
                orders == null ? List.of() : List.copyOf(orders));
    }

    @Override
    public Mono<AgentCartMutationResultVo> addCartItem(
            Long userId,
            String requestId,
            Long skuId,
            int quantity) {
        requireUserId(userId);
        AgentCartAddRequestDto request = new AgentCartAddRequestDto();
        request.setRequestId(requestId);
        request.setSkuId(skuId);
        request.setQuantity(quantity);
        return protectedCall(Mono.defer(() -> {
            verifyConfiguration();
            return cartClient.post()
                    .uri(CART_ITEMS_PATH, userId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            Mono.error(new PersonalActionRejectedException(
                                    "Cart action was rejected"
                            )))
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            upstreamError("Cart service", response.statusCode()))
                    .bodyToMono(AgentCartMutationResultVo.class);
        }), "Cart service");
    }

    @Override
    public Mono<AgentOrderCancellationCandidateDto> getCancellationCandidate(
            Long userId,
            int recentPosition) {
        requireUserId(userId);
        return protectedCall(Mono.defer(() -> {
            verifyConfiguration();
            return orderClient.get()
                    .uri(
                            ORDER_CANCELLATION_CANDIDATE_PATH,
                            userId,
                            recentPosition
                    )
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            Mono.error(new PersonalActionRejectedException(
                                    "Cancellation candidate was not found"
                            )))
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            upstreamError(
                                    "Order service",
                                    response.statusCode()
                            ))
                    .bodyToMono(AgentOrderCancellationCandidateDto.class)
                    .switchIfEmpty(Mono.error(
                            new PersonalDataUnavailableException(
                                    "Order service returned no candidate"
                            )
                    ));
        }), "Order service");
    }

    @Override
    public Mono<AgentOrderCancellationResultVo> cancelOrder(
            Long userId,
            String requestId,
            String orderNo) {
        requireUserId(userId);
        AgentOrderCancelRequestDto request = new AgentOrderCancelRequestDto();
        request.setRequestId(requestId);
        request.setOrderNo(orderNo);
        return protectedCall(Mono.defer(() -> {
            verifyConfiguration();
            return orderClient.post()
                    .uri(ORDER_CANCELLATIONS_PATH, userId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            Mono.error(new PersonalActionRejectedException(
                                    "Order cancellation was rejected"
                            )))
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            upstreamError(
                                    "Order service",
                                    response.statusCode()
                            ))
                    .bodyToMono(AgentOrderCancellationResultVo.class);
        }), "Order service");
    }

    private <T> Mono<T> protectedCall(Mono<T> operation, String serviceName) {
        return operation
                .timeout(requestTimeout)
                .onErrorMap(failure -> mapFailure(failure, serviceName));
    }

    private Mono<? extends Throwable> upstreamError(
            String serviceName,
            HttpStatusCode status) {
        return Mono.error(new PersonalDataUnavailableException(
                serviceName + " returned HTTP " + status.value()
        ));
    }

    private Throwable mapFailure(Throwable failure, String serviceName) {
        if (failure instanceof AgentAuthenticationException
                || failure instanceof PersonalDataUnavailableException
                || failure instanceof PersonalActionRejectedException) {
            return failure;
        }
        if (failure instanceof TimeoutException) {
            return new PersonalDataUnavailableException(
                    serviceName + " request timed out",
                    failure
            );
        }
        return new PersonalDataUnavailableException(
                serviceName + " request failed",
                failure
        );
    }

    private void verifyConfiguration() {
        if (!StringUtils.hasText(internalToken)) {
            throw new PersonalDataUnavailableException(
                    "Internal API token is not configured"
            );
        }
        if (requestTimeout == null || requestTimeout.isZero()
                || requestTimeout.isNegative()) {
            throw new PersonalDataUnavailableException(
                    "Personal data request timeout is invalid"
            );
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Resolved user ID is invalid");
        }
    }
}
