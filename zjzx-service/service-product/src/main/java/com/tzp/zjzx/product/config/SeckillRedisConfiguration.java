package com.tzp.zjzx.product.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SeckillProperties.class)
public class SeckillRedisConfiguration {

    @Bean
    public RedisScript<Long> seckillReserveScript() {
        return script("redis/seckill_reserve.lua");
    }

    @Bean
    public RedisScript<Long> seckillRollbackScript() {
        return script("redis/seckill_rollback.lua");
    }

    private RedisScript<Long> script(String resource) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource(resource)));
        script.setResultType(Long.class);
        return script;
    }
}
