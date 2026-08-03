package com.tzp.zjzx.feign;

import com.tzp.zjzx.common.anno.EnableUserLoginAuthInterceptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableUserLoginAuthInterceptor
@EnableFeignClients(basePackages = {"com.tzp.zjzx"})
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)  // 排除数据库的自动化配置，Cart微服务不需要访问数据库
public class CartApplication8513 {

    public static void main(String[] args) {
        SpringApplication.run(CartApplication8513.class , args) ;
    }

}
