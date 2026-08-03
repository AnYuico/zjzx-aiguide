package com.tzp.zjzx.pay;

import com.tzp.zjzx.common.anno.EnableUserLoginAuthInterceptor;
import com.tzp.zjzx.pay.properties.AlipayProperties;
import com.tzp.zjzx.mq.config.MqInfrastructureConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;


@SpringBootApplication
@EnableFeignClients(basePackages = {"com.tzp.zjzx.feign"})
@EnableUserLoginAuthInterceptor
@EnableConfigurationProperties(value = { AlipayProperties.class })
@Import(MqInfrastructureConfiguration.class)
public class PayApplication8515 {

    public static void main(String[] args) {
        SpringApplication.run(PayApplication8515.class,args);
    }
}
