package com.tzp.zjzx.feign.guide;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

public class ProductGuideFeignConfiguration {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Bean
    public RequestInterceptor productGuideInternalTokenInterceptor(
            @Value("${zjzx.internal-api.token}") String internalApiToken) {
        if (!StringUtils.hasText(internalApiToken)) {
            throw new IllegalStateException("ZJZX internal API token must be configured");
        }
        return requestTemplate -> requestTemplate.header(
                INTERNAL_TOKEN_HEADER,
                internalApiToken
        );
    }
}
