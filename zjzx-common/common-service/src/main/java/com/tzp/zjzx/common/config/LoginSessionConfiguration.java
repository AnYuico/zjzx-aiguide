package com.tzp.zjzx.common.config;

import com.tzp.zjzx.common.security.LoginSessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration(proxyBeanMethods = false)
public class LoginSessionConfiguration {

    @Bean
    public LoginSessionService loginSessionService(RedisTemplate<String, String> redisTemplate) {
        return new LoginSessionService(redisTemplate);
    }
}
