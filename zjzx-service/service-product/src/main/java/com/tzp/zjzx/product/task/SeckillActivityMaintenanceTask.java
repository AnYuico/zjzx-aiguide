package com.tzp.zjzx.product.task;

import com.tzp.zjzx.model.entity.seckill.SeckillActivity;
import com.tzp.zjzx.model.entity.seckill.SeckillSku;
import com.tzp.zjzx.model.enums.SeckillActivityStatus;
import com.tzp.zjzx.product.config.SeckillProperties;
import com.tzp.zjzx.product.mapper.SeckillActivityMapper;
import com.tzp.zjzx.product.mapper.SeckillSkuMapper;
import com.tzp.zjzx.product.service.SeckillActivityLifecycleService;
import com.tzp.zjzx.product.service.SeckillRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class SeckillActivityMaintenanceTask {

    private final SeckillActivityMapper activityMapper;
    private final SeckillSkuMapper skuMapper;
    private final SeckillActivityLifecycleService lifecycleService;
    private final SeckillRedisService redisService;
    private final SeckillProperties properties;

    public SeckillActivityMaintenanceTask(
            SeckillActivityMapper activityMapper,
            SeckillSkuMapper skuMapper,
            SeckillActivityLifecycleService lifecycleService,
            SeckillRedisService redisService,
            SeckillProperties properties) {
        this.activityMapper = activityMapper;
        this.skuMapper = skuMapper;
        this.lifecycleService = lifecycleService;
        this.redisService = redisService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${zjzx.seckill.activity-scan-delay-ms:10000}",
            initialDelayString = "${zjzx.seckill.activity-scan-initial-delay-ms:30000}")
    public void endExpiredActivities() {
        for (SeckillActivity activity : activityMapper.findEndingCandidates()) {
            try {
                endIfSettled(activity);
            } catch (RuntimeException ex) {
                log.warn("Seckill activity maintenance failed: activityId={}",
                        activity.getId(), ex);
            }
        }
    }

    private void endIfSettled(SeckillActivity activity) {
        List<SeckillSku> skus = skuMapper.findByActivityId(activity.getId());
        redisService.deactivate(activity.getId(), skus);
        if (!Integer.valueOf(SeckillActivityStatus.ENDING.getCode())
                .equals(activity.getStatus())) {
            lifecycleService.beginEnding(activity.getId());
        }

        Date settleFrom = activity.getEndTime();
        if (Integer.valueOf(SeckillActivityStatus.ENDING.getCode())
                .equals(activity.getStatus())
                && settleFrom.after(new Date())
                && activity.getUpdateTime() != null) {
            settleFrom = activity.getUpdateTime();
        }
        long elapsed = System.currentTimeMillis() - settleFrom.getTime();
        if (elapsed < Math.max(0L, properties.getEndingGraceMs())
                || skuMapper.countOutstandingRequests(activity.getId()) > 0
                || hasPending(skus, activity.getId())) {
            return;
        }
        if (elapsed < Math.max(properties.getEndingGraceMs(),
                properties.getForceFinishGraceMs())
                && hasInFlight(skus, activity.getId())) {
            return;
        }

        for (SeckillSku sku : skus) {
            Integer redisStock = redisService.stock(activity.getId(), sku.getSkuId());
            if (redisStock != null && !redisStock.equals(sku.getAvailableStock())) {
                log.warn("Seckill stock reconciled from MySQL: activityId={}, skuId={}, redis={}, mysql={}",
                        activity.getId(), sku.getSkuId(),
                        redisStock, sku.getAvailableStock());
            }
            redisService.finish(activity.getId(), sku);
        }
        lifecycleService.finish(activity.getId());
    }

    private boolean hasPending(List<SeckillSku> skus, Long activityId) {
        for (SeckillSku sku : skus) {
            if (redisService.hasPending(activityId, sku.getSkuId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInFlight(List<SeckillSku> skus, Long activityId) {
        for (SeckillSku sku : skus) {
            if (redisService.hasInFlightResult(activityId, sku.getSkuId())) {
                return true;
            }
        }
        return false;
    }
}
