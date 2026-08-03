package com.tzp.zjzx.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tzp.zjzx.gateway.config.SeckillRateLimitProperties;
import com.tzp.zjzx.model.vo.common.LoginPrincipal;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.utils.LoginSessionKey;
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
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SeckillRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Pattern SUBMIT_PATH = Pattern.compile(
            "^/api/product/seckill/auth/activity/(\\d+)/sku/(\\d+)/submit$");

    private final RedisTemplate<String, String> loginRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> rateLimitScript;
    private final SeckillRateLimitProperties properties;

    public SeckillRateLimitGlobalFilter(
            RedisTemplate<String, String> loginRedisTemplate,
            StringRedisTemplate stringRedisTemplate,
            @Qualifier("seckillRateLimitScript") RedisScript<Long> rateLimitScript,
            SeckillRateLimitProperties properties) {
        this.loginRedisTemplate = loginRedisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getMethod() != HttpMethod.POST) {
            return chain.filter(exchange);
        }
        Matcher matcher = SUBMIT_PATH.matcher(request.getURI().getPath());
        if (!matcher.matches()) {
            return chain.filter(exchange);
        }

        LoginPrincipal principal = getPrincipal(request);
        if (principal == null || principal.getUserId() == null) {
            return out(exchange, ResultCodeEnum.LOGIN_AUTH, HttpStatus.OK);
        }

        String activityId = matcher.group(1);
        String ip = clientIp(request);
        long bucket = Instant.now().getEpochSecond();
        List<String> keys = List.of(
                "rate:seckill:ip:" + ip + ":" + bucket,
                "rate:seckill:user:" + principal.getUserId() + ":" + bucket,
                "rate:seckill:activity:" + activityId + ":" + bucket);
        Long limitedDimension = stringRedisTemplate.execute(rateLimitScript, keys,
                Integer.toString(properties.getIpPerSecond()),
                Integer.toString(properties.getUserPerSecond()),
                Integer.toString(properties.getActivityPerSecond()),
                "2");
        if (limitedDimension == null || limitedDimension != 0L) {
            return out(exchange, ResultCodeEnum.SECKILL_RATE_LIMITED,
                    HttpStatus.TOO_MANY_REQUESTS);
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
        } catch (RuntimeException ex) {
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

    private Mono<Void> out(ServerWebExchange exchange,
                           ResultCodeEnum resultCode,
                           HttpStatus status) {
        Result<Object> result = Result.build(null, resultCode);
        byte[] body = JSONObject.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders()
                .set("Content-Type", "application/json;charset=UTF-8");
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return 10;
    }
}

