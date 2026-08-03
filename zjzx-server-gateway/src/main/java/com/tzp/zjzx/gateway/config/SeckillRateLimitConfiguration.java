package com.tzp.zjzx.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SeckillRateLimitProperties.class)
public class SeckillRateLimitConfiguration {

    @Bean
    public RedisScript<Long> seckillRateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("redis/seckill_rate_limit.lua")));
        script.setResultType(Long.class);
        return script;
    }
}

