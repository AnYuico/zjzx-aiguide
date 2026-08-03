package com.tzp.zjzx.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.utils.SeckillRedisKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SeckillOrderResultService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${zjzx.seckill.result-retention-days:7}")
    private long resultRetentionDays;

    public SeckillOrderResultService(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void processing(SeckillOrderRequestedEvent event) {
        write(event, 1, null, "PROCESSING");
    }

    public void success(SeckillOrderRequestedEvent event, Long orderId) {
        write(event, 2, orderId, "SUCCESS");
    }

    public void failed(SeckillOrderRequestedEvent event, String message) {
        write(event, 3, null, message == null ? "FAILED" : message);
    }

    public void cancelled(SeckillOrderRequestedEvent event, Long orderId) {
        write(event, 4, orderId, "CANCELLED");
    }

    private void write(SeckillOrderRequestedEvent event, int status,
                       Long orderId, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", event.getRequestId());
        result.put("userId", event.getUserId());
        result.put("orderNo", event.getOrderNo());
        result.put("status", status);
        result.put("orderId", orderId);
        result.put("message", message);

        String key = SeckillRedisKeys.results(event.getActivityId(), event.getSkuId());
        try {
            redisTemplate.opsForHash().put(
                    key, event.getRequestId(), objectMapper.writeValueAsString(result));
            redisTemplate.expire(key, Duration.ofDays(Math.max(1L, resultRetentionDays)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize seckill result", ex);
        }
    }
}
