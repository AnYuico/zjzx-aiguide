package com.tzp.zjzx.product.task;

import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.product.config.SeckillProperties;
import com.tzp.zjzx.product.service.SeckillMqPublisher;
import com.tzp.zjzx.product.service.SeckillRedisService;
import com.tzp.zjzx.utils.SeckillRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class SeckillPendingPublishTask {

    private final StringRedisTemplate redisTemplate;
    private final SeckillRedisService redisService;
    private final SeckillMqPublisher mqPublisher;
    private final SeckillProperties properties;

    public SeckillPendingPublishTask(StringRedisTemplate redisTemplate,
                                     SeckillRedisService redisService,
                                     SeckillMqPublisher mqPublisher,
                                     SeckillProperties properties) {
        this.redisTemplate = redisTemplate;
        this.redisService = redisService;
        this.mqPublisher = mqPublisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${zjzx.seckill.publish-scan-delay-ms:5000}",
            initialDelayString = "${zjzx.seckill.publish-scan-initial-delay-ms:15000}")
    public void republishPending() {
        Set<String> members = redisTemplate.opsForSet()
                .members(SeckillRedisKeys.ACTIVE_SKUS);
        if (members == null) {
            return;
        }
        long before = System.currentTimeMillis() - 2000L;
        for (String member : members) {
            long[] ids = parseMember(member);
            for (SeckillOrderRequestedEvent event
                    : redisService.findPending(ids[0], ids[1], before, 50)) {
                try {
                    if (redisService.publishAttempts(event)
                            >= Math.max(1, properties.getMaxPublishAttempts())) {
                        redisService.rollback(event, "MQ_PUBLISH_FAILED");
                    } else {
                        mqPublisher.publish(event);
                    }
                } catch (RuntimeException ex) {
                    log.warn("Seckill pending publish compensation failed: requestId={}",
                            event.getRequestId(), ex);
                }
            }
        }
    }

    private long[] parseMember(String member) {
        String[] parts = member.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid active seckill SKU: " + member);
        }
        return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
    }
}
