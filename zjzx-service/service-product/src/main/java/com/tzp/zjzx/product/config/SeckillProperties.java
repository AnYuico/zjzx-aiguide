package com.tzp.zjzx.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zjzx.seckill")
public class SeckillProperties {

    private int resultRetentionDays = 7;
    private int maxPublishAttempts = 20;
    private long publishScanDelayMs = 5000;
    private long publishScanInitialDelayMs = 15000;
    private long activityScanDelayMs = 10000;
    private long activityScanInitialDelayMs = 30000;
    private long endingGraceMs = 300000;
    private long forceFinishGraceMs = 1800000;
}
