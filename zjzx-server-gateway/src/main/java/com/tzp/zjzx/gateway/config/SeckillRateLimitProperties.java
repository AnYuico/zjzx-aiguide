package com.tzp.zjzx.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zjzx.seckill.rate-limit")
public class SeckillRateLimitProperties {

    private int ipPerSecond = 20;
    private int userPerSecond = 5;
    private int activityPerSecond = 200;
}

