package com.tzp.zjzx.manager;

import com.tzp.zjzx.manager.properties.ImageUploadProperties;
import com.tzp.zjzx.manager.properties.InternalApiProperties;
import com.tzp.zjzx.manager.properties.MinioProperties;
import com.tzp.zjzx.manager.properties.ProductServiceProperties;
import com.tzp.zjzx.manager.properties.UserProperties;
import com.tzp.zjzx.mq.config.MqInfrastructureConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.context.annotation.Import;

@EnableScheduling
@SpringBootApplication
@ComponentScan(basePackages = {"com.tzp.zjzx"})
@EnableTransactionManagement
@EnableConfigurationProperties(value = {
        UserProperties.class,
        MinioProperties.class,
        ImageUploadProperties.class,
        InternalApiProperties.class,
        ProductServiceProperties.class
})
@Import(MqInfrastructureConfiguration.class)
public class ManagerApplication8501 {

    public static void main(String[] args) {
        SpringApplication.run(ManagerApplication8501.class, args);
    }
}
