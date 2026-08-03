package com.tzp.zjzx.order.service;

import com.tzp.zjzx.model.entity.seckill.SeckillOrderRequest;
import com.tzp.zjzx.model.enums.SeckillRequestStatus;
import com.tzp.zjzx.order.mapper.SeckillOrderRequestMapper;
import com.tzp.zjzx.order.mapper.SeckillOrderStockMapper;
import com.tzp.zjzx.utils.SeckillRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.time.Duration;

@Slf4j
@Service
public class SeckillStockReturnService {

    private final SeckillOrderRequestMapper requestMapper;
    private final SeckillOrderStockMapper stockMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> restoreCancelledScript;

    @Value("${zjzx.seckill.result-retention-days:7}")
    private long resultRetentionDays;

    public SeckillStockReturnService(
            SeckillOrderRequestMapper requestMapper,
            SeckillOrderStockMapper stockMapper,
            StringRedisTemplate redisTemplate,
            @Qualifier("seckillRestoreCancelledScript")
            RedisScript<Long> restoreCancelledScript) {
        this.requestMapper = requestMapper;
        this.stockMapper = stockMapper;
        this.redisTemplate = redisTemplate;
        this.restoreCancelledScript = restoreCancelledScript;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean returnAfterPhysicalRelease(String orderNo) {
        SeckillOrderRequest request = requestMapper.selectByOrderNo(orderNo);
        if (request == null || Integer.valueOf(1).equals(request.getStockReturned())) {
            return false;
        }
        if (Integer.valueOf(SeckillRequestStatus.CANCELLED.getCode())
                .equals(request.getStatus())) {
            afterCommit(() -> restoreRedis(request));
            return true;
        }
        if (requestMapper.markCancelled(orderNo) != 1) {
            return false;
        }
        if (stockMapper.restore(request.getSeckillSkuId()) != 1) {
            throw new IllegalStateException(
                    "Failed to restore seckill activity stock: " + orderNo);
        }
        afterCommit(() -> restoreRedis(request));
        return true;
    }

    private void restoreRedis(SeckillOrderRequest request) {
        try {
            Long result = redisTemplate.execute(restoreCancelledScript,
                    List.of(
                            SeckillRedisKeys.stock(
                                    request.getActivityId(), request.getSkuId()),
                            SeckillRedisKeys.results(
                                    request.getActivityId(), request.getSkuId()),
                            SeckillRedisKeys.returns(
                                    request.getActivityId(), request.getSkuId())),
                    request.getRequestId(),
                    Long.toString(request.getUserId()),
                    request.getOrderNo(),
                    Long.toString(Duration.ofDays(
                            Math.max(1L, resultRetentionDays)).getSeconds()));
            if (result == null) {
                log.warn("Redis seckill stock restore needs reconciliation: requestId={}, result={}",
                        request.getRequestId(), result);
                return;
            }
            requestMapper.markStockReturned(
                    request.getRequestId(), SeckillRequestStatus.CANCELLED.getCode());
        } catch (RuntimeException ex) {
            log.warn("Redis seckill stock restore failed: requestId={}",
                    request.getRequestId(), ex);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }
}
