package com.tzp.zjzx.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tzp.zjzx.gateway.config.AgentRateLimitProperties;
import com.tzp.zjzx.model.vo.common.LoginPrincipal;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.utils.LoginSessionKey;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Component
public class AgentRateLimitGlobalFilter implements GlobalFilter, Ordered {

    static final String CHAT_PATH = "/api/agent/auth/guide/chat";
    private static final int RATE_LIMIT_CODE = 242;
    private static final String RATE_LIMIT_MESSAGE = "请求过于频繁，请稍后重试";

    private final RedisTemplate<String, String> loginRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> rateLimitScript;
    private final AgentRateLimitProperties properties;
    private final MeterRegistry meterRegistry;

    public AgentRateLimitGlobalFilter(
            RedisTemplate<String, String> loginRedisTemplate,
            StringRedisTemplate stringRedisTemplate,
            @Qualifier("agentRateLimitScript") RedisScript<Long> rateLimitScript,
            AgentRateLimitProperties properties,
            MeterRegistry meterRegistry) {
        this.loginRedisTemplate = loginRedisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getMethod() != HttpMethod.POST
                || !CHAT_PATH.equals(request.getURI().getPath())) {
            return chain.filter(exchange);
        }

        LoginPrincipal principal = getPrincipal(request);
        if (principal == null || principal.getUserId() == null) {
            return rateLimited(exchange, HttpStatus.UNAUTHORIZED, "authentication");
        }

        String token = request.getHeaders().getFirst("token");
        long minuteBucket = Instant.now().getEpochSecond() / 60;
        List<String> keys = List.of(
                "rate:agent:user:" + principal.getUserId() + ":" + minuteBucket,
                "rate:agent:ip:" + clientIp(request) + ":" + minuteBucket,
                "rate:agent:session:" + tokenDigest(token) + ":" + minuteBucket
        );
        Long limitedDimension = stringRedisTemplate.execute(
                rateLimitScript,
                keys,
                Integer.toString(properties.getUserPerMinute()),
                Integer.toString(properties.getIpPerMinute()),
                Integer.toString(properties.getSessionPerMinute()),
                "120"
        );
        if (limitedDimension == null || limitedDimension != 0L) {
            return rateLimited(
                    exchange,
                    HttpStatus.TOO_MANY_REQUESTS,
                    limitedDimensionName(limitedDimension)
            );
        }
        return chain.filter(exchange);
    }

    private LoginPrincipal getPrincipal(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst("token");
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String json = loginRedisTemplate.opsForValue()
                .get(LoginSessionKey.userToken(token));
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, LoginPrincipal.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String clientIp(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private String tokenDigest(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String limitedDimensionName(Long dimension) {
        if (dimension == null) {
            return "redis";
        }
        return switch (dimension.intValue()) {
            case 1 -> "user";
            case 2 -> "ip";
            case 3 -> "session";
            default -> "unknown";
        };
    }

    private Mono<Void> rateLimited(
            ServerWebExchange exchange,
            HttpStatus status,
            String reason) {
        meterRegistry.counter(
                "zjzx.gateway.agent.rate_limit.rejections",
                "reason",
                reason
        ).increment();
        Result<Object> result = Result.build(null, RATE_LIMIT_CODE, RATE_LIMIT_MESSAGE);
        byte[] body = JSONObject.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders()
                .set("Content-Type", "application/json;charset=UTF-8");
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            exchange.getResponse().getHeaders().set("Retry-After", "60");
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
