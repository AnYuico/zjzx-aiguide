package com.tzp.zjzx.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration(proxyBeanMethods = false)
public class SeckillRedisScriptConfiguration {

    @Bean
    public RedisScript<Long> seckillRollbackScript() {
        return script("redis/seckill_rollback.lua");
    }

    @Bean
    public RedisScript<Long> seckillRestoreCancelledScript() {
        return script("redis/seckill_restore_cancelled.lua");
    }

    private RedisScript<Long> script(String resource) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(resource)));
        script.setResultType(Long.class);
        return script;
    }
}
