package com.tzp.zjzx.feign.service;

import com.alibaba.fastjson2.JSON;
import com.tzp.zjzx.ai.contract.dto.AgentCartAddRequestDto;
import com.tzp.zjzx.ai.contract.vo.AgentCartMutationResultVo;
import com.tzp.zjzx.feign.exception.AgentCartMutationException;
import com.tzp.zjzx.feign.product.ProductFeignClient;
import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.entity.h5.CartInfo;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class AgentCartMutationService {

    private static final long REPLAYED = -1L;
    private static final long REQUEST_ID_CONFLICT = -2L;
    private static final long QUANTITY_EXCEEDED = -3L;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> agentCartAddScript;
    private final ProductFeignClient productFeignClient;
    private final String internalApiToken;
    private final Duration idempotencyTtl;
    private final int maximumRequestQuantity;
    private final int maximumTotalQuantity;

    public AgentCartMutationService(
            StringRedisTemplate redisTemplate,
            @Qualifier("agentCartAddScript") RedisScript<Long> agentCartAddScript,
            ProductFeignClient productFeignClient,
            @Value("${zjzx.internal-api.token}") String internalApiToken,
            @Value("${zjzx.cart.agent-action-idempotency-ttl-days:30}")
            int idempotencyTtlDays,
            @Value("${zjzx.cart.agent-action-max-request-quantity:10}")
            int maximumRequestQuantity,
            @Value("${zjzx.cart.agent-action-max-total-quantity:99}")
            int maximumTotalQuantity) {
        this.redisTemplate = redisTemplate;
        this.agentCartAddScript = agentCartAddScript;
        this.productFeignClient = productFeignClient;
        this.internalApiToken = internalApiToken;
        this.idempotencyTtl = Duration.ofDays(idempotencyTtlDays);
        this.maximumRequestQuantity = maximumRequestQuantity;
        this.maximumTotalQuantity = maximumTotalQuantity;
    }

    public AgentCartMutationResultVo addItem(
            Long userId,
            AgentCartAddRequestDto request) {
        validate(userId, request);
        String cartKey = "user:cart:" + userId;
        String idempotencyKey = "user:cart:agent-action:"
                + userId + ":" + request.getRequestId();
        String fingerprint = request.getSkuId() + ":" + request.getQuantity();

        String existingFingerprint = redisTemplate.opsForValue()
                .get(idempotencyKey);
        if (existingFingerprint != null) {
            if (!fingerprint.equals(existingFingerprint)) {
                throw conflict("Request ID was already used with another payload");
            }
            return result(false, true);
        }

        ProductSkuInternalDto sku = loadAvailableSku(request.getSkuId());
        CartInfo newItem = new CartInfo();
        newItem.setUserId(userId);
        newItem.setSkuId(request.getSkuId());
        newItem.setSkuName(sku.getSkuName());
        newItem.setImgUrl(sku.getThumbImg());
        newItem.setCartPrice(sku.getSalePrice());
        newItem.setSkuNum(request.getQuantity());
        newItem.setIsChecked(1);
        Date now = new Date();
        newItem.setCreateTime(now);
        newItem.setUpdateTime(now);

        Long scriptResult;
        try {
            scriptResult = redisTemplate.execute(
                    agentCartAddScript,
                    List.of(cartKey, idempotencyKey),
                    String.valueOf(idempotencyTtl.toSeconds()),
                    fingerprint,
                    String.valueOf(request.getSkuId()),
                    String.valueOf(request.getQuantity()),
                    String.valueOf(maximumTotalQuantity),
                    JSON.toJSONString(newItem)
            );
        } catch (AgentCartMutationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AgentCartMutationException(
                    AgentCartMutationException.Reason.UNAVAILABLE,
                    "Redis cart mutation failed",
                    exception
            );
        }

        if (scriptResult == null) {
            throw unavailable("Redis cart mutation returned no result");
        }
        if (scriptResult == REPLAYED) {
            return result(false, true);
        }
        if (scriptResult == REQUEST_ID_CONFLICT) {
            throw conflict("Request ID was already used with another payload");
        }
        if (scriptResult == QUANTITY_EXCEEDED) {
            throw conflict("Cart quantity limit exceeded");
        }
        if (scriptResult <= 0) {
            throw unavailable("Redis cart mutation returned an invalid result");
        }
        return result(true, false);
    }

    private ProductSkuInternalDto loadAvailableSku(Long skuId) {
        Result<ProductSkuInternalDto> response;
        try {
            response = productFeignClient.getBySkuId(internalApiToken, skuId);
        } catch (RuntimeException exception) {
            throw new AgentCartMutationException(
                    AgentCartMutationException.Reason.UNAVAILABLE,
                    "Product service request failed",
                    exception
            );
        }
        if (response == null
                || !ResultCodeEnum.SUCCESS.getCode().equals(response.getCode())) {
            throw unavailable("Product service returned an invalid response");
        }
        ProductSkuInternalDto sku = response.getData();
        if (sku == null
                || !Integer.valueOf(1).equals(sku.getStatus())
                || Integer.valueOf(1).equals(sku.getIsDeleted())
                || sku.getSalePrice() == null) {
            throw conflict("Product is not currently available");
        }
        return sku;
    }

    private void validate(Long userId, AgentCartAddRequestDto request) {
        if (userId == null || userId <= 0 || request == null
                || request.getSkuId() == null || request.getSkuId() <= 0
                || request.getQuantity() == null || request.getQuantity() < 1
                || request.getQuantity() > maximumRequestQuantity
                || !StringUtils.hasText(request.getRequestId())) {
            throw new AgentCartMutationException(
                    AgentCartMutationException.Reason.INVALID_REQUEST,
                    "Invalid agent cart request"
            );
        }
        try {
            UUID.fromString(request.getRequestId());
        } catch (IllegalArgumentException exception) {
            throw new AgentCartMutationException(
                    AgentCartMutationException.Reason.INVALID_REQUEST,
                    "Request ID must be a UUID"
            );
        }
        if (idempotencyTtl.isZero() || idempotencyTtl.isNegative()
                || maximumTotalQuantity < maximumRequestQuantity) {
            throw unavailable("Agent cart action configuration is invalid");
        }
    }

    private AgentCartMutationResultVo result(boolean applied, boolean replayed) {
        AgentCartMutationResultVo result = new AgentCartMutationResultVo();
        result.setApplied(applied);
        result.setReplayed(replayed);
        return result;
    }

    private AgentCartMutationException conflict(String message) {
        return new AgentCartMutationException(
                AgentCartMutationException.Reason.CONFLICT,
                message
        );
    }

    private AgentCartMutationException unavailable(String message) {
        return new AgentCartMutationException(
                AgentCartMutationException.Reason.UNAVAILABLE,
                message
        );
    }
}
