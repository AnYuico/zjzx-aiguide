package com.tzp.zjzx.utils;

public final class LoginSessionKey {

    public static final String ADMIN_TOKEN_PREFIX = "auth:admin:token:";
    public static final String USER_TOKEN_PREFIX = "auth:user:token:";

    private static final String ADMIN_USER_PREFIX = "auth:admin:user:";
    private static final String USER_PREFIX = "auth:user:user:";

    private LoginSessionKey() {
    }

    public static String adminToken(String token) {
        return ADMIN_TOKEN_PREFIX + token;
    }

    public static String adminUserTokens(Long userId) {
        return ADMIN_USER_PREFIX + userId + ":tokens";
    }

    public static String adminAuthVersion(Long userId) {
        return ADMIN_USER_PREFIX + userId + ":version";
    }

    public static String userToken(String token) {
        return USER_TOKEN_PREFIX + token;
    }

    public static String userTokens(Long userId) {
        return USER_PREFIX + userId + ":tokens";
    }

    public static String userAuthVersion(Long userId) {
        return USER_PREFIX + userId + ":version";
    }
}
