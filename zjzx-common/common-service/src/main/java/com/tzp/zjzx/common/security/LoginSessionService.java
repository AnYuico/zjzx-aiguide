package com.tzp.zjzx.common.security;

import com.alibaba.fastjson.JSON;
import com.tzp.zjzx.model.vo.common.LoginPrincipal;
import com.tzp.zjzx.utils.LoginSessionKey;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class LoginSessionService {

    public static final Duration ADMIN_SESSION_TTL = Duration.ofMinutes(30);
    public static final Duration USER_SESSION_TTL = Duration.ofDays(30);

    private final RedisTemplate<String, String> redisTemplate;

    public LoginSessionService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void createAdminSession(String token, Long userId, String username) {
        createSession(
                token,
                userId,
                username,
                LoginSessionKey.adminToken(token),
                LoginSessionKey.adminUserTokens(userId),
                LoginSessionKey.adminAuthVersion(userId),
                ADMIN_SESSION_TTL
        );
    }

    public LoginPrincipal getAdminPrincipal(String token) {
        return getPrincipal(
                token,
                LoginSessionKey.adminToken(token),
                LoginSessionKey::adminUserTokens,
                LoginSessionKey::adminAuthVersion
        );
    }

    public void refreshAdminSession(String token, Long userId) {
        redisTemplate.expire(LoginSessionKey.adminToken(token), ADMIN_SESSION_TTL);
        redisTemplate.expire(LoginSessionKey.adminUserTokens(userId), ADMIN_SESSION_TTL);
    }

    public void logoutAdminSession(String token) {
        logoutSession(token, LoginSessionKey.adminToken(token), LoginSessionKey::adminUserTokens);
    }

    public void revokeAdminSessionsAfterCommit(Long userId) {
        runAfterCommit(() -> revokeSessions(
                userId,
                LoginSessionKey.adminUserTokens(userId),
                LoginSessionKey.adminAuthVersion(userId),
                LoginSessionKey.ADMIN_TOKEN_PREFIX
        ));
    }

    public void createUserSession(String token, Long userId, String username) {
        createSession(
                token,
                userId,
                username,
                LoginSessionKey.userToken(token),
                LoginSessionKey.userTokens(userId),
                LoginSessionKey.userAuthVersion(userId),
                USER_SESSION_TTL
        );
    }

    public LoginPrincipal getUserPrincipal(String token) {
        return getPrincipal(
                token,
                LoginSessionKey.userToken(token),
                LoginSessionKey::userTokens,
                LoginSessionKey::userAuthVersion
        );
    }

    public void logoutUserSession(String token) {
        logoutSession(token, LoginSessionKey.userToken(token), LoginSessionKey::userTokens);
    }

    public void revokeUserSessionsAfterCommit(Long userId) {
        runAfterCommit(() -> revokeSessions(
                userId,
                LoginSessionKey.userTokens(userId),
                LoginSessionKey.userAuthVersion(userId),
                LoginSessionKey.USER_TOKEN_PREFIX
        ));
    }

    private void createSession(String token,
                               Long userId,
                               String username,
                               String tokenKey,
                               String userTokensKey,
                               String versionKey,
                               Duration ttl) {
        long authVersion = getOrCreateAuthVersion(versionKey);
        LoginPrincipal principal = new LoginPrincipal(userId, username, authVersion);
        redisTemplate.opsForValue().set(tokenKey, JSON.toJSONString(principal), ttl);
        redisTemplate.opsForSet().add(userTokensKey, token);
        redisTemplate.expire(userTokensKey, ttl);
    }

    private LoginPrincipal getPrincipal(String token,
                                        String tokenKey,
                                        UserKeyFactory userTokensKeyFactory,
                                        UserKeyFactory versionKeyFactory) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String principalJson = redisTemplate.opsForValue().get(tokenKey);
        if (!StringUtils.hasText(principalJson)) {
            return null;
        }

        LoginPrincipal principal;
        try {
            principal = JSON.parseObject(principalJson, LoginPrincipal.class);
        } catch (RuntimeException exception) {
            redisTemplate.delete(tokenKey);
            return null;
        }
        if (principal == null || principal.getUserId() == null || principal.getAuthVersion() == null) {
            redisTemplate.delete(tokenKey);
            return null;
        }

        String currentVersion = redisTemplate.opsForValue().get(versionKeyFactory.create(principal.getUserId()));
        if (!StringUtils.hasText(currentVersion)
                || !String.valueOf(principal.getAuthVersion()).equals(currentVersion)) {
            redisTemplate.delete(tokenKey);
            redisTemplate.opsForSet().remove(userTokensKeyFactory.create(principal.getUserId()), token);
            return null;
        }
        return principal;
    }

    private void logoutSession(String token, String tokenKey, UserKeyFactory userTokensKeyFactory) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String principalJson = redisTemplate.opsForValue().get(tokenKey);
        redisTemplate.delete(tokenKey);
        if (!StringUtils.hasText(principalJson)) {
            return;
        }
        try {
            LoginPrincipal principal = JSON.parseObject(principalJson, LoginPrincipal.class);
            if (principal != null && principal.getUserId() != null) {
                redisTemplate.opsForSet().remove(userTokensKeyFactory.create(principal.getUserId()), token);
            }
        } catch (RuntimeException ignored) {
            // The token key is already removed; malformed legacy data needs no further cleanup.
        }
    }

    private long getOrCreateAuthVersion(String versionKey) {
        redisTemplate.opsForValue().setIfAbsent(versionKey, "1");
        String version = redisTemplate.opsForValue().get(versionKey);
        if (!StringUtils.hasText(version)) {
            throw new IllegalStateException("Unable to initialize login authorization version");
        }
        return Long.parseLong(version);
    }

    private void revokeSessions(Long userId, String userTokensKey, String versionKey, String tokenPrefix) {
        redisTemplate.opsForValue().setIfAbsent(versionKey, "1");
        redisTemplate.opsForValue().increment(versionKey);
        Set<String> tokens = redisTemplate.opsForSet().members(userTokensKey);
        if (tokens != null && !tokens.isEmpty()) {
            Collection<String> tokenKeys = tokens.stream()
                    .map(tokenPrefix::concat)
                    .collect(Collectors.toList());
            redisTemplate.delete(tokenKeys);
        }
        redisTemplate.delete(userTokensKey);
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    @FunctionalInterface
    private interface UserKeyFactory {
        String create(Long userId);
    }
}
