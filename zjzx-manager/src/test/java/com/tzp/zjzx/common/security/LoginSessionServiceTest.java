package com.tzp.zjzx.common.security;

import com.alibaba.fastjson.JSON;
import com.tzp.zjzx.model.vo.common.LoginPrincipal;
import com.tzp.zjzx.utils.LoginSessionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginSessionServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private LoginSessionService loginSessionService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        loginSessionService = new LoginSessionService(redisTemplate);
    }

    @Test
    void storesOnlyMinimalAdminPrincipal() {
        when(valueOperations.get(LoginSessionKey.adminAuthVersion(7L))).thenReturn("1");

        loginSessionService.createAdminSession("token-1", 7L, "admin");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq(LoginSessionKey.adminToken("token-1")),
                jsonCaptor.capture(),
                eq(Duration.ofMinutes(30))
        );
        LoginPrincipal principal = JSON.parseObject(jsonCaptor.getValue(), LoginPrincipal.class);
        assertEquals(7L, principal.getUserId());
        assertEquals("admin", principal.getUsername());
        assertEquals(1L, principal.getAuthVersion());
        assertFalse(jsonCaptor.getValue().contains("password"));
        assertFalse(jsonCaptor.getValue().contains("phone"));
        assertFalse(jsonCaptor.getValue().contains("avatar"));
    }

    @Test
    void rejectsTokenWhenAuthorizationVersionChanged() {
        LoginPrincipal cachedPrincipal = new LoginPrincipal(7L, "admin", 1L);
        when(valueOperations.get(LoginSessionKey.adminToken("token-1")))
                .thenReturn(JSON.toJSONString(cachedPrincipal));
        when(valueOperations.get(LoginSessionKey.adminAuthVersion(7L))).thenReturn("2");

        LoginPrincipal result = loginSessionService.getAdminPrincipal("token-1");

        assertEquals(null, result);
        verify(redisTemplate).delete(LoginSessionKey.adminToken("token-1"));
        verify(setOperations).remove(LoginSessionKey.adminUserTokens(7L), "token-1");
    }
}
