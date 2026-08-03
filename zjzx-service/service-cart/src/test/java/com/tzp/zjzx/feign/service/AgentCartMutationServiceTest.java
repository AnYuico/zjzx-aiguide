package com.tzp.zjzx.feign.service;

import com.tzp.zjzx.ai.contract.dto.AgentCartAddRequestDto;
import com.tzp.zjzx.ai.contract.vo.AgentCartMutationResultVo;
import com.tzp.zjzx.feign.exception.AgentCartMutationException;
import com.tzp.zjzx.feign.product.ProductFeignClient;
import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentCartMutationServiceTest {

    private static final String REQUEST_ID =
            "d0b2abec-b950-4a6f-94f6-8f54647d2db6";

    private final StringRedisTemplate redisTemplate =
            mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations =
            mock(ValueOperations.class);
    private final RedisScript<Long> redisScript = mock(RedisScript.class);
    private final ProductFeignClient productFeignClient =
            mock(ProductFeignClient.class);
    private AgentCartMutationService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new AgentCartMutationService(
                redisTemplate,
                redisScript,
                productFeignClient,
                "internal-secret",
                30,
                10,
                99
        );
    }

    @Test
    void appliesCartIncrementAndIdempotencyMarkerInOneLuaCall() {
        AgentCartAddRequestDto request = request(14L, 2);
        when(valueOperations.get(idempotencyKey())).thenReturn(null);
        when(productFeignClient.getBySkuId("internal-secret", 14L))
                .thenReturn(Result.build(availableSku(), ResultCodeEnum.SUCCESS));
        when(redisTemplate.execute(
                eq(redisScript),
                eq(List.of("user:cart:33", idempotencyKey())),
                eq("2592000"),
                eq("14:2"),
                eq("14"),
                eq("2"),
                eq("99"),
                anyString()
        )).thenReturn(2L);

        AgentCartMutationResultVo result = service.addItem(33L, request);

        assertTrue(result.getApplied());
        assertFalse(result.getReplayed());
        verify(redisTemplate).execute(
                eq(redisScript),
                eq(List.of("user:cart:33", idempotencyKey())),
                eq("2592000"),
                eq("14:2"),
                eq("14"),
                eq("2"),
                eq("99"),
                anyString()
        );
    }

    @Test
    void replayReturnsSuccessWithoutCallingProductService() {
        when(valueOperations.get(idempotencyKey())).thenReturn("14:2");

        AgentCartMutationResultVo result =
                service.addItem(33L, request(14L, 2));

        assertFalse(result.getApplied());
        assertTrue(result.getReplayed());
        verify(productFeignClient, never()).getBySkuId(
                anyString(),
                eq(14L)
        );
    }

    @Test
    void concurrentLuaReplayAlsoReturnsSuccess() {
        when(valueOperations.get(idempotencyKey())).thenReturn(null);
        when(productFeignClient.getBySkuId("internal-secret", 14L))
                .thenReturn(Result.build(availableSku(), ResultCodeEnum.SUCCESS));
        when(redisTemplate.execute(
                eq(redisScript),
                eq(List.of("user:cart:33", idempotencyKey())),
                eq("2592000"),
                eq("14:2"),
                eq("14"),
                eq("2"),
                eq("99"),
                anyString()
        )).thenReturn(-1L);

        AgentCartMutationResultVo result =
                service.addItem(33L, request(14L, 2));

        assertTrue(result.getReplayed());
    }

    @Test
    void rejectsRequestIdReuseWithDifferentPayload() {
        when(valueOperations.get(idempotencyKey())).thenReturn("14:1");

        AgentCartMutationException exception = assertThrows(
                AgentCartMutationException.class,
                () -> service.addItem(33L, request(14L, 2))
        );

        assertEquals(
                AgentCartMutationException.Reason.CONFLICT,
                exception.getReason()
        );
        verify(productFeignClient, never()).getBySkuId(
                anyString(),
                eq(14L)
        );
    }

    private AgentCartAddRequestDto request(Long skuId, Integer quantity) {
        AgentCartAddRequestDto request = new AgentCartAddRequestDto();
        request.setRequestId(REQUEST_ID);
        request.setSkuId(skuId);
        request.setQuantity(quantity);
        return request;
    }

    private ProductSkuInternalDto availableSku() {
        ProductSkuInternalDto sku = new ProductSkuInternalDto();
        sku.setId(14L);
        sku.setSkuName("Mac mini 16G");
        sku.setThumbImg("http://image.test/mac.png");
        sku.setSalePrice(new BigDecimal("1999.00"));
        sku.setStatus(1);
        sku.setIsDeleted(0);
        return sku;
    }

    private String idempotencyKey() {
        return "user:cart:agent-action:33:" + REQUEST_ID;
    }
}
