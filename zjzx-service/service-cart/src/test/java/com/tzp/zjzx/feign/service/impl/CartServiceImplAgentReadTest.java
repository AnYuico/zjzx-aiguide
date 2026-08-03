package com.tzp.zjzx.feign.service.impl;

import com.alibaba.fastjson2.JSON;
import com.tzp.zjzx.ai.contract.vo.AgentCartItemVo;
import com.tzp.zjzx.model.entity.h5.CartInfo;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CartServiceImplAgentReadTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void returnsOnlySanitizedCartFieldsForBoundUser() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        HashOperations hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        CartInfo cartInfo = new CartInfo();
        cartInfo.setUserId(33L);
        cartInfo.setSkuId(14L);
        cartInfo.setSkuName("Mac mini 16G");
        cartInfo.setImgUrl("http://image.test/mac.png");
        cartInfo.setCartPrice(new BigDecimal("1999.00"));
        cartInfo.setSkuNum(1);
        cartInfo.setIsChecked(0);
        cartInfo.setCreateTime(new Date());
        when(hashOperations.values("user:cart:33"))
                .thenReturn(List.of(JSON.toJSONString(cartInfo)));
        CartServiceImpl service = new CartServiceImpl();
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);

        List<AgentCartItemVo> result = service.getAgentCart(33L);

        assertEquals(1, result.size());
        assertEquals(14L, result.get(0).getSkuId());
        assertEquals(new BigDecimal("1999.00"), result.get(0).getCartPrice());
        assertFalse(result.get(0).getSelected());
    }
}
