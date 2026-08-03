package com.tzp.zjzx.feign.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration(proxyBeanMethods = false)
public class AgentCartActionConfiguration {

    @Bean
    public RedisScript<Long> agentCartAddScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("redis/agent_cart_add.lua")
        ));
        script.setResultType(Long.class);
        return script;
    }
}
