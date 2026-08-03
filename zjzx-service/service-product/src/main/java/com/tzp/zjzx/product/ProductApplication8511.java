package com.tzp.zjzx.product;

import com.tzp.zjzx.common.exception.GlobalExceptionHandler;
import com.tzp.zjzx.common.anno.EnableUserLoginAuthInterceptor;
import com.tzp.zjzx.mq.config.MqInfrastructureConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@SpringBootApplication
@EnableUserLoginAuthInterceptor
@EnableScheduling
@Import({MqInfrastructureConfiguration.class, GlobalExceptionHandler.class})
public class ProductApplication8511 {
    public static void main(String[] args) {
        SpringApplication.run(ProductApplication8511.class, args);
    }
}

