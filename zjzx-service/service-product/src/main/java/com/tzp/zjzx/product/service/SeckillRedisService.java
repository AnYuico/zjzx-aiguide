package com.tzp.zjzx.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.entity.seckill.SeckillActivity;
import com.tzp.zjzx.model.entity.seckill.SeckillSku;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.product.config.SeckillProperties;
import com.tzp.zjzx.utils.SeckillRedisKeys;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Set;

@Service
public class SeckillRedisService {

    private static final String META_STATUS = "status";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> reserveScript;
    private final RedisScript<Long> rollbackScript;
    private final ObjectMapper objectMapper;
    private final SeckillProperties properties;

    public SeckillRedisService(
            StringRedisTemplate redisTemplate,
            @Qualifier("seckillReserveScript") RedisScript<Long> reserveScript,
            @Qualifier("seckillRollbackScript") RedisScript<Long> rollbackScript,
            ObjectMapper objectMapper,
            SeckillProperties properties) {
        this.redisTemplate = redisTemplate;
        this.reserveScript = reserveScript;
        this.rollbackScript = rollbackScript;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void preheat(SeckillActivity activity, List<SeckillSku> skus) {
        long ttlSeconds = ttlSeconds(activity.getEndTime());
        for (SeckillSku sku : skus) {
            Map<String, String> meta = new HashMap<>();
            meta.put(META_STATUS, "0");
            meta.put("startAt", Long.toString(activity.getStartTime().getTime()));
            meta.put("endAt", Long.toString(activity.getEndTime().getTime()));
            meta.put("seckillSkuId", Long.toString(sku.getId()));
            meta.put("price", sku.getSeckillPrice().toPlainString());

            String metaKey = SeckillRedisKeys.meta(activity.getId(), sku.getSkuId());
            redisTemplate.opsForHash().putAll(metaKey, meta);
            redisTemplate.opsForValue().set(
                    SeckillRedisKeys.stock(activity.getId(), sku.getSkuId()),
                    Integer.toString(sku.getAvailableStock()), ttlSeconds, TimeUnit.SECONDS);
            expireAdmissionKeys(activity.getId(), sku.getSkuId(), ttlSeconds);
            redisTemplate.opsForSet().add(SeckillRedisKeys.ACTIVE_SKUS,
                    SeckillRedisKeys.member(activity.getId(), sku.getSkuId()));
        }
    }

    public void activate(SeckillActivity activity, List<SeckillSku> skus) {
        for (SeckillSku sku : skus) {
            redisTemplate.opsForHash().put(
                    SeckillRedisKeys.meta(activity.getId(), sku.getSkuId()),
                    META_STATUS, "1");
        }
    }

    public void deactivate(Long activityId, List<SeckillSku> skus) {
        for (SeckillSku sku : skus) {
            redisTemplate.opsForHash().put(
                    SeckillRedisKeys.meta(activityId, sku.getSkuId()),
                    META_STATUS, "0");
        }
    }

    public List<SeckillOrderRequestedEvent> findPending(
            Long activityId, Long skuId, long beforeMillis, int limit) {
        Set<String> requestIds = redisTemplate.opsForZSet().rangeByScore(
                SeckillRedisKeys.pending(activityId, skuId),
                0, beforeMillis, 0, Math.max(1, limit));
        List<SeckillOrderRequestedEvent> events = new ArrayList<>();
        if (requestIds == null) {
            return events;
        }
        for (String requestId : requestIds) {
            Object payload = redisTemplate.opsForHash().get(
                    SeckillRedisKeys.payloads(activityId, skuId), requestId);
            if (payload == null) {
                continue;
            }
            try {
                events.add(objectMapper.readValue(
                        payload.toString(), SeckillOrderRequestedEvent.class));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException(
                        "Invalid pending seckill event: " + requestId, ex);
            }
        }
        return events;
    }

    public long publishAttempts(SeckillOrderRequestedEvent event) {
        Object raw = redisTemplate.opsForHash().get(
                SeckillRedisKeys.attempts(event.getActivityId(), event.getSkuId()),
                event.getRequestId());
        return raw == null ? 0L : Long.parseLong(raw.toString());
    }

    public void rollback(SeckillOrderRequestedEvent event, String message) {
        List<String> keys = List.of(
                SeckillRedisKeys.stock(event.getActivityId(), event.getSkuId()),
                SeckillRedisKeys.buyers(event.getActivityId(), event.getSkuId()),
                SeckillRedisKeys.payloads(event.getActivityId(), event.getSkuId()),
                SeckillRedisKeys.results(event.getActivityId(), event.getSkuId()),
                SeckillRedisKeys.pending(event.getActivityId(), event.getSkuId()),
                SeckillRedisKeys.attempts(event.getActivityId(), event.getSkuId()),
                SeckillRedisKeys.rollbacks(event.getActivityId(), event.getSkuId()));
        long ttlSeconds = Duration.ofDays(
                Math.max(1, properties.getResultRetentionDays())).getSeconds();
        Long result = redisTemplate.execute(rollbackScript, keys,
                Long.toString(event.getUserId()),
                event.getRequestId(),
                event.getOrderNo(),
                message == null ? "FAILED" : message,
                Long.toString(ttlSeconds));
        if (result == null) {
            throw new IllegalStateException("Seckill rollback script returned no result");
        }
    }

    public boolean hasPending(Long activityId, Long skuId) {
        Long size = redisTemplate.opsForZSet().size(
                SeckillRedisKeys.pending(activityId, skuId));
        return size != null && size > 0;
    }

    public boolean hasInFlightResult(Long activityId, Long skuId) {
        List<Object> values = redisTemplate.opsForHash().values(
                SeckillRedisKeys.results(activityId, skuId));
        for (Object value : values) {
            try {
                JsonNode node = objectMapper.readTree(value.toString());
                int status = node.path("status").asInt(-1);
                if (status == 0 || status == 1) {
                    return true;
                }
            } catch (JsonProcessingException ex) {
                return true;
            }
        }
        return false;
    }

    public Integer stock(Long activityId, Long skuId) {
        String value = redisTemplate.opsForValue().get(
                SeckillRedisKeys.stock(activityId, skuId));
        return value == null ? null : Integer.valueOf(value);
    }

    public void finish(Long activityId, SeckillSku sku) {
        redisTemplate.opsForHash().put(
                SeckillRedisKeys.meta(activityId, sku.getSkuId()),
                META_STATUS, "0");
        redisTemplate.opsForValue().set(
                SeckillRedisKeys.stock(activityId, sku.getSkuId()),
                Integer.toString(sku.getAvailableStock()),
                Math.max(1, properties.getResultRetentionDays()), TimeUnit.DAYS);
        redisTemplate.opsForSet().remove(SeckillRedisKeys.ACTIVE_SKUS,
                SeckillRedisKeys.member(activityId, sku.getSkuId()));
    }

    public SeckillOrderRequestedEvent reserve(Long activityId,
                                               Long skuId,
                                               Long userId,
                                               Long userAddressId,
                                               String requestId) {
        validateRequest(activityId, skuId, userId, userAddressId, requestId);
        Map<Object, Object> meta = redisTemplate.opsForHash()
                .entries(SeckillRedisKeys.meta(activityId, skuId));
        if (meta.isEmpty()) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_ACTIVE);
        }

        String orderNo = UUID.randomUUID().toString().replace("-", "");
        Date acceptedAt = new Date();
        SeckillOrderRequestedEvent event = new SeckillOrderRequestedEvent(
                "seckill.order:" + requestId,
                requestId,
                activityId,
                parseLong(meta.get("seckillSkuId")),
                skuId,
                userId,
                userAddressId,
                orderNo,
                parseDecimal(meta.get("price")),
                acceptedAt);

        String payload = writePayload(event);
        long ttlSeconds = ttlSeconds(new Date(parseLong(meta.get("endAt"))));
        List<String> keys = List.of(
                SeckillRedisKeys.meta(activityId, skuId),
                SeckillRedisKeys.stock(activityId, skuId),
                SeckillRedisKeys.buyers(activityId, skuId),
                SeckillRedisKeys.payloads(activityId, skuId),
                SeckillRedisKeys.results(activityId, skuId),
                SeckillRedisKeys.pending(activityId, skuId));
        Long result = redisTemplate.execute(reserveScript, keys,
                Long.toString(userId),
                requestId,
                orderNo,
                Long.toString(userAddressId),
                Long.toString(acceptedAt.getTime()),
                payload,
                Long.toString(ttlSeconds));
        if (result == null) {
            throw new IllegalStateException("Seckill reserve script returned no result");
        }
        if (result == 2L) {
            return readExistingEvent(activityId, skuId, requestId);
        }
        if (result == -1L) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_ACTIVE);
        }
        if (result == -2L) {
            throw new MyException(ResultCodeEnum.SECKILL_SOLD_OUT);
        }
        if (result == -3L) {
            throw new MyException(ResultCodeEnum.SECKILL_USER_LIMIT);
        }
        if (result != 1L) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
        return event;
    }

    private SeckillOrderRequestedEvent readExistingEvent(Long activityId,
                                                         Long skuId,
                                                         String requestId) {
        Object payload = redisTemplate.opsForHash().get(
                SeckillRedisKeys.payloads(activityId, skuId), requestId);
        if (payload == null) {
            throw new MyException(ResultCodeEnum.SECKILL_DUPLICATE_REQUEST);
        }
        try {
            return objectMapper.readValue(payload.toString(), SeckillOrderRequestedEvent.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid cached seckill event", ex);
        }
    }

    private void validateRequest(Long activityId, Long skuId, Long userId,
                                 Long userAddressId, String requestId) {
        if (activityId == null || activityId <= 0 || skuId == null || skuId <= 0
                || userId == null || userId <= 0
                || userAddressId == null || userAddressId <= 0
                || !StringUtils.hasText(requestId) || requestId.length() > 64) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
    }

    private String writePayload(SeckillOrderRequestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize seckill event", ex);
        }
    }

    private long ttlSeconds(Date endTime) {
        long retention = Duration.ofDays(properties.getResultRetentionDays()).getSeconds();
        long untilEnd = Math.max(1L, (endTime.getTime() - System.currentTimeMillis()) / 1000L);
        return Math.addExact(untilEnd, retention);
    }

    private void expireAdmissionKeys(Long activityId, Long skuId, long ttlSeconds) {
        redisTemplate.expire(SeckillRedisKeys.meta(activityId, skuId),
                ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(SeckillRedisKeys.buyers(activityId, skuId),
                ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(SeckillRedisKeys.payloads(activityId, skuId),
                ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(SeckillRedisKeys.results(activityId, skuId),
                ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(SeckillRedisKeys.pending(activityId, skuId),
                ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(SeckillRedisKeys.attempts(activityId, skuId),
                ttlSeconds, TimeUnit.SECONDS);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_ACTIVE);
        }
        return Long.valueOf(value.toString());
    }

    private BigDecimal parseDecimal(Object value) {
        if (value == null) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_ACTIVE);
        }
        return new BigDecimal(value.toString());
    }
}
