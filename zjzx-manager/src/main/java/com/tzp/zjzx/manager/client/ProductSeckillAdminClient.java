package com.tzp.zjzx.manager.client;

import com.tzp.zjzx.common.security.InternalApiAuth;
import com.tzp.zjzx.manager.properties.InternalApiProperties;
import com.tzp.zjzx.manager.properties.ProductServiceProperties;
import com.tzp.zjzx.model.dto.seckill.SeckillActivityCreateDto;
import com.tzp.zjzx.model.vo.common.Result;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

@Service
public class ProductSeckillAdminClient {

    private final RestTemplate restTemplate;
    private final ProductServiceProperties productProperties;
    private final InternalApiProperties internalApiProperties;

    public ProductSeckillAdminClient(
            RestTemplateBuilder restTemplateBuilder,
            ProductServiceProperties productProperties,
            InternalApiProperties internalApiProperties) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.productProperties = productProperties;
        this.internalApiProperties = internalApiProperties;
    }

    public Result<?> list(Integer page, Integer limit, Integer status) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(endpoint("/api/product/internal/seckill/activities"))
                .queryParam("page", page)
                .queryParam("limit", limit);
        if (status != null) {
            builder.queryParam("status", status);
        }
        return exchange(builder.build(true).toUri(), HttpMethod.GET, null);
    }

    public Result<?> getById(Long activityId) {
        return exchange(uri("/api/product/internal/seckill/activities/" + activityId),
                HttpMethod.GET, null);
    }

    public Result<?> create(SeckillActivityCreateDto dto) {
        return exchange(uri("/api/product/internal/seckill/activities"),
                HttpMethod.POST, dto);
    }

    public Result<?> update(Long activityId, SeckillActivityCreateDto dto) {
        return exchange(uri("/api/product/internal/seckill/activities/" + activityId),
                HttpMethod.PUT, dto);
    }

    public Result<?> publish(Long activityId) {
        return exchange(uri("/api/product/internal/seckill/activities/"
                + activityId + "/publish"), HttpMethod.POST, null);
    }

    public Result<?> offline(Long activityId) {
        return exchange(uri("/api/product/internal/seckill/activities/"
                + activityId + "/offline"), HttpMethod.POST, null);
    }

    private Result<?> exchange(URI uri, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(InternalApiAuth.HEADER_NAME, internalApiProperties.getToken());
        HttpEntity<?> entity = body == null
                ? new HttpEntity<>(headers)
                : new HttpEntity<>(body, headers);
        Result<?> result = restTemplate.exchange(
                uri, method, entity, Result.class).getBody();
        if (result == null) {
            throw new IllegalStateException("Product service returned an empty response");
        }
        return result;
    }

    private URI uri(String path) {
        return URI.create(endpoint(path));
    }

    private String endpoint(String path) {
        String baseUrl = productProperties.getBaseUrl();
        return (baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl) + path;
    }
}
