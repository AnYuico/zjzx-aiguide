package com.tzp.zjzx.feign.service;

import com.tzp.zjzx.mq.MqEventIds;
import com.tzp.zjzx.model.enums.OrderSource;
import com.tzp.zjzx.model.event.cart.CartCleanupItemEvent;
import com.tzp.zjzx.model.event.cart.CartCleanupRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartCleanupMqServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedisScript<Long> redisScript;

    @Test
    void executesAtomicCleanupWithMergedSkuQuantities() {
        CartCleanupMqService service =
                new CartCleanupMqService(redisTemplate, redisScript, 30);
        CartCleanupRequestedEvent event = event(List.of(
                new CartCleanupItemEvent(1001L, 1),
                new CartCleanupItemEvent(1001L, 2),
                new CartCleanupItemEvent(1002L, 1)));
        when(redisTemplate.execute(
                eq(redisScript),
                eq(List.of("user:cart:20", "user:cart:cleanup:20:order-10")),
                eq("2592000"), eq("1001"), eq("3"), eq("1002"), eq("1")))
                .thenReturn(2L);

        service.cleanup(event);

        verify(redisTemplate).execute(
                eq(redisScript),
                eq(List.of("user:cart:20", "user:cart:cleanup:20:order-10")),
                eq("2592000"), eq("1001"), eq("3"), eq("1002"), eq("1"));
    }

    @Test
    void acceptsAlreadyProcessedEventAsSuccessful() {
        CartCleanupMqService service =
                new CartCleanupMqService(redisTemplate, redisScript, 30);
        CartCleanupRequestedEvent event =
                event(List.of(new CartCleanupItemEvent(1001L, 1)));
        when(redisTemplate.execute(
                eq(redisScript),
                eq(List.of("user:cart:20", "user:cart:cleanup:20:order-10")),
                eq("2592000"), eq("1001"), eq("1")))
                .thenReturn(-1L);

        service.cleanup(event);

        verify(redisTemplate).execute(
                eq(redisScript),
                eq(List.of("user:cart:20", "user:cart:cleanup:20:order-10")),
                eq("2592000"), eq("1001"), eq("1"));
    }

    @Test
    void rejectsBuyNowEventBeforeTouchingRedis() {
        CartCleanupMqService service =
                new CartCleanupMqService(redisTemplate, redisScript, 30);
        CartCleanupRequestedEvent event =
                event(List.of(new CartCleanupItemEvent(1001L, 1)));
        event.setOrderSource(OrderSource.BUY_NOW.getCode());

        assertThrows(IllegalArgumentException.class, () -> service.cleanup(event));

        verifyNoInteractions(redisTemplate);
    }

    private CartCleanupRequestedEvent event(List<CartCleanupItemEvent> items) {
        return new CartCleanupRequestedEvent(
                MqEventIds.cartCleanup("order-10"),
                "order-10",
                20L,
                OrderSource.CART.getCode(),
                items,
                new Date());
    }
}
