package com.tzp.zjzx.user;

import com.tzp.zjzx.common.anno.EnableUserLoginAuthInterceptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;


@SpringBootApplication
@ComponentScan(basePackages = {"com.tzp.zjzx"})
@EnableUserLoginAuthInterceptor
public class UserApplication8512 {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication8512.class, args);
    }
}
