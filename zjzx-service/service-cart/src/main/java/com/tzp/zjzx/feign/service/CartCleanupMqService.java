package com.tzp.zjzx.feign.service;

import com.tzp.zjzx.mq.MqEventIds;
import com.tzp.zjzx.model.enums.OrderSource;
import com.tzp.zjzx.model.event.cart.CartCleanupItemEvent;
import com.tzp.zjzx.model.event.cart.CartCleanupRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
public class CartCleanupMqService {

    private static final int MAX_EVENT_ITEMS = 200;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> cartCleanupScript;
    private final long idempotencyTtlSeconds;

    public CartCleanupMqService(
            StringRedisTemplate redisTemplate,
            @Qualifier("cartCleanupScript") RedisScript<Long> cartCleanupScript,
            @Value("${zjzx.cart.cleanup-idempotency-ttl-days:30}") long ttlDays) {
        if (ttlDays <= 0) {
            throw new IllegalArgumentException(
                    "zjzx.cart.cleanup-idempotency-ttl-days must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.cartCleanupScript = cartCleanupScript;
        this.idempotencyTtlSeconds = Duration.ofDays(ttlDays).getSeconds();
    }

    public void cleanup(CartCleanupRequestedEvent event) {
        Map<Long, Integer> quantities = validateAndMerge(event);
        List<String> arguments = new ArrayList<>(1 + quantities.size() * 2);
        arguments.add(Long.toString(idempotencyTtlSeconds));
        quantities.forEach((skuId, skuNum) -> {
            arguments.add(Long.toString(skuId));
            arguments.add(Integer.toString(skuNum));
        });

        Long result = redisTemplate.execute(
                cartCleanupScript,
                List.of(cartKey(event.getUserId()), processedKey(event)),
                arguments.toArray());
        if (result == null) {
            throw new IllegalStateException("Cart cleanup script returned no result");
        }
        if (result < 0) {
            log.debug("Cart cleanup event already processed: {}", event.getEventId());
        }
    }

    private Map<Long, Integer> validateAndMerge(CartCleanupRequestedEvent event) {
        if (event == null
                || !StringUtils.hasText(event.getEventId())
                || !StringUtils.hasText(event.getOrderNo())
                || event.getEventId().length() > 128
                || event.getOrderNo().length() > 64
                || event.getUserId() == null
                || event.getUserId() <= 0
                || !Integer.valueOf(OrderSource.CART.getCode()).equals(event.getOrderSource())
                || CollectionUtils.isEmpty(event.getItems())
                || event.getItems().size() > MAX_EVENT_ITEMS
                || !MqEventIds.cartCleanup(event.getOrderNo()).equals(event.getEventId())) {
            throw new IllegalArgumentException("Invalid cart cleanup event");
        }

        Map<Long, Integer> quantities = new TreeMap<>();
        try {
            for (CartCleanupItemEvent item : event.getItems()) {
                if (item == null || item.getSkuId() == null || item.getSkuId() <= 0
                        || item.getSkuNum() == null || item.getSkuNum() <= 0) {
                    throw new IllegalArgumentException("Invalid cart cleanup item");
                }
                quantities.merge(item.getSkuId(), item.getSkuNum(), Math::addExact);
            }
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Cart cleanup quantity overflow", ex);
        }
        return quantities;
    }

    private String cartKey(Long userId) {
        return "user:cart:" + userId;
    }

    private String processedKey(CartCleanupRequestedEvent event) {
        return "user:cart:cleanup:" + event.getUserId() + ":" + event.getOrderNo();
    }
}
