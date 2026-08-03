package com.tzp.zjzx.order.service;

import com.tzp.zjzx.model.entity.seckill.SeckillOrderRequest;
import com.tzp.zjzx.model.enums.SeckillRequestStatus;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.order.mapper.SeckillOrderRequestMapper;
import com.tzp.zjzx.utils.SeckillRedisKeys;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.List;

@Service
public class SeckillAdmissionRollbackService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rollbackScript;
    private final SeckillOrderRequestMapper requestMapper;

    @Value("${zjzx.seckill.result-retention-days:7}")
    private long resultRetentionDays;

    public SeckillAdmissionRollbackService(
            StringRedisTemplate redisTemplate,
            @Qualifier("seckillRollbackScript") RedisScript<Long> rollbackScript,
            SeckillOrderRequestMapper requestMapper) {
        this.redisTemplate = redisTemplate;
        this.rollbackScript = rollbackScript;
        this.requestMapper = requestMapper;
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
        long ttlSeconds = Duration.ofDays(Math.max(1L, resultRetentionDays)).getSeconds();
        Long result = redisTemplate.execute(rollbackScript, keys,
                Long.toString(event.getUserId()),
                event.getRequestId(),
                event.getOrderNo(),
                message == null ? "FAILED" : message,
                Long.toString(ttlSeconds));
        if (result == null) {
            throw new IllegalStateException("Seckill rollback script returned no result");
        }
        requestMapper.markStockReturned(
                event.getRequestId(), SeckillRequestStatus.FAILED.getCode());
    }

    public SeckillOrderRequestedEvent fromRequest(SeckillOrderRequest request) {
        Date acceptedAt = request.getCreateTime() == null
                ? new Date() : request.getCreateTime();
        return new SeckillOrderRequestedEvent(
                "seckill.order:" + request.getRequestId(),
                request.getRequestId(),
                request.getActivityId(),
                request.getSeckillSkuId(),
                request.getSkuId(),
                request.getUserId(),
                request.getUserAddressId(),
                request.getOrderNo(),
                null,
                acceptedAt);
    }
}
