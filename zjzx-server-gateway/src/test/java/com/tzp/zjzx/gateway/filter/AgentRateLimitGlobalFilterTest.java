package com.tzp.zjzx.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.tzp.zjzx.gateway.config.AgentRateLimitProperties;
import com.tzp.zjzx.model.vo.common.LoginPrincipal;
import com.tzp.zjzx.utils.LoginSessionKey;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRateLimitGlobalFilterTest {

    @Mock
    private RedisTemplate<String, String> loginRedisTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisScript<Long> rateLimitScript;

    private AgentRateLimitGlobalFilter filter;

    @BeforeEach
    void setUp() {
        AgentRateLimitProperties properties = new AgentRateLimitProperties();
        properties.setUserPerMinute(10);
        properties.setIpPerMinute(30);
        properties.setSessionPerMinute(15);
        when(loginRedisTemplate.opsForValue()).thenReturn(valueOperations);
        filter = new AgentRateLimitGlobalFilter(
                loginRedisTemplate,
                stringRedisTemplate,
                rateLimitScript,
                properties,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void forwardsChatRequestWithinUserAndIpLimits() {
        String token = "valid-token";
        LoginPrincipal principal = new LoginPrincipal(33L, "test", 1L);
        when(valueOperations.get(LoginSessionKey.userToken(token)))
                .thenReturn(JSON.toJSONString(principal));
        when(stringRedisTemplate.execute(
                eq(rateLimitScript),
                anyList(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(0L);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = chatExchange(token);

        filter.filter(exchange, ignored -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();

        assertTrue(chainInvoked.get());
    }

    @Test
    void rejectsChatRequestWhenAnyDimensionExceedsLimit() {
        String token = "limited-token";
        LoginPrincipal principal = new LoginPrincipal(33L, "test", 1L);
        when(valueOperations.get(LoginSessionKey.userToken(token)))
                .thenReturn(JSON.toJSONString(principal));
        when(stringRedisTemplate.execute(
                eq(rateLimitScript),
                anyList(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(1L);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = chatExchange(token);

        filter.filter(exchange, ignored -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("60", exchange.getResponse().getHeaders().getFirst("Retry-After"));
    }

    @Test
    void rejectsChatRequestWhenSessionLimitIsExceeded() {
        String token = "session-limited-token";
        LoginPrincipal principal = new LoginPrincipal(33L, "test", 1L);
        when(valueOperations.get(LoginSessionKey.userToken(token)))
                .thenReturn(JSON.toJSONString(principal));
        when(stringRedisTemplate.execute(
                eq(rateLimitScript),
                anyList(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(3L);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = chatExchange(token);

        filter.filter(exchange, ignored -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
    }

    private MockServerWebExchange chatExchange(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post(AgentRateLimitGlobalFilter.CHAT_PATH)
                        .header("token", token)
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                        .build()
        );
    }
}
