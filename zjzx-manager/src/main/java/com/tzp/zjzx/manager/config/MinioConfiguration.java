package com.tzp.zjzx.manager.config;

import com.tzp.zjzx.manager.properties.MinioProperties;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfiguration {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndPointUrl())
                .credentials(properties.getAccessKey(), properties.getSecreKey())
                .build();
    }
}
