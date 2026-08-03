package com.tzp.zjzx.order;

import com.tzp.zjzx.common.anno.EnableUserLoginAuthInterceptor;
import com.tzp.zjzx.common.anno.EnableUserTokenFeignInterceptor;
import com.tzp.zjzx.mq.config.MqInfrastructureConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Import;



@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
@EnableUserLoginAuthInterceptor
@EnableUserTokenFeignInterceptor
@EnableFeignClients(basePackages = {"com.tzp.zjzx"})
@Import(MqInfrastructureConfiguration.class)
public class OrderApplication8514 {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication8514.class, args);
    }
}
