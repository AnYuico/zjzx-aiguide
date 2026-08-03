package com.tzp.zjzx.user.testdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zjzx.test-data")
public class TestDataProperties {

    private boolean enabled;
    private String apiKey;
    private int maxBatchSize = 100;
}
