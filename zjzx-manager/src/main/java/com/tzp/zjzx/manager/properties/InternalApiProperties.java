package com.tzp.zjzx.manager.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "zjzx.internal-api")
public class InternalApiProperties {

    @NotBlank
    private String token;
}
