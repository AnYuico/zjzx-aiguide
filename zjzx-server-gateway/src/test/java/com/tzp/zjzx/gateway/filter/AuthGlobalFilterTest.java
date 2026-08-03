package com.tzp.zjzx.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.tzp.zjzx.model.vo.common.LoginPrincipal;
import com.tzp.zjzx.utils.LoginSessionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthGlobalFilterTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private AuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        filter = new AuthGlobalFilter(redisTemplate);
    }

    @Test
    void forwardsAuthenticatedRequestWhenVersionMatches() {
        String token = "valid-token";
        LoginPrincipal principal = new LoginPrincipal(3L, "user", 2L);
        when(valueOperations.get(LoginSessionKey.userToken(token)))
                .thenReturn(JSON.toJSONString(principal));
        when(valueOperations.get(LoginSessionKey.userAuthVersion(3L))).thenReturn("2");
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/user/userInfo/auth/getCurrentUserInfo")
                        .header("token", token)
                        .build()
        );

        filter.filter(exchange, ignored -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();

        assertTrue(chainInvoked.get());
    }

    @Test
    void rejectsAndDeletesTokenWhenVersionChanged() {
        String token = "revoked-token";
        LoginPrincipal principal = new LoginPrincipal(3L, "user", 1L);
        when(valueOperations.get(LoginSessionKey.userToken(token)))
                .thenReturn(JSON.toJSONString(principal));
        when(valueOperations.get(LoginSessionKey.userAuthVersion(3L))).thenReturn("2");
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/order/orderInfo/auth/1")
                        .header("token", token)
                        .build()
        );

        filter.filter(exchange, ignored -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainInvoked.get());
        verify(redisTemplate).delete(LoginSessionKey.userToken(token));
        verify(setOperations).remove(LoginSessionKey.userTokens(3L), token);
    }

    @Test
    void rejectsAgentChatWithoutValidMallSession() {
        String token = "invalid-agent-token";
        when(valueOperations.get(LoginSessionKey.userToken(token))).thenReturn(null);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/agent/auth/guide/chat")
                        .header("token", token)
                        .build()
        );

        filter.filter(exchange, ignored -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainInvoked.get());
    }

    @Test
    void rejectsPaymentSubmissionWithoutValidMallSession() {
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/order/alipay/submitAlipay/order-1")
                        .build()
        );

        filter.filter(exchange, ignored -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainInvoked.get());
    }

    @Test
    void forwardsAlipayCallbackWithoutMallSession() {
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/order/alipay/callback/notify")
                        .build()
        );

        filter.filter(exchange, ignored -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();

        assertTrue(chainInvoked.get());
    }
}
