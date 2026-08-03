package com.tzp.zjzx.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tzp.zjzx.model.vo.common.LoginPrincipal;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.utils.LoginSessionKey;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final RedisTemplate<String, String> redisTemplate;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public AuthGlobalFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (requiresUserAuthentication(path)
                && !hasValidUserSession(exchange.getRequest())) {
            return out(exchange.getResponse(), ResultCodeEnum.LOGIN_AUTH);
        }
        return chain.filter(exchange);
    }

    private boolean requiresUserAuthentication(String path) {
        return antPathMatcher.match("/api/**/auth/**", path)
                || antPathMatcher.match("/api/order/alipay/submitAlipay/**", path);
    }

    private boolean hasValidUserSession(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst("token");
        if (!StringUtils.hasText(token)) {
            return false;
        }

        String tokenKey = LoginSessionKey.userToken(token);
        String principalJson = redisTemplate.opsForValue().get(tokenKey);
        if (!StringUtils.hasText(principalJson)) {
            return false;
        }

        LoginPrincipal principal;
        try {
            principal = JSON.parseObject(principalJson, LoginPrincipal.class);
        } catch (RuntimeException exception) {
            redisTemplate.delete(tokenKey);
            return false;
        }
        if (principal == null || principal.getUserId() == null || principal.getAuthVersion() == null) {
            redisTemplate.delete(tokenKey);
            return false;
        }

        String currentVersion = redisTemplate.opsForValue()
                .get(LoginSessionKey.userAuthVersion(principal.getUserId()));
        if (!String.valueOf(principal.getAuthVersion()).equals(currentVersion)) {
            redisTemplate.delete(tokenKey);
            redisTemplate.opsForSet().remove(LoginSessionKey.userTokens(principal.getUserId()), token);
            return false;
        }
        return true;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum) {
        Result<Object> result = Result.build(null, resultCodeEnum);
        byte[] body = JSONObject.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        return response.writeWith(Mono.just(buffer));
    }
}
