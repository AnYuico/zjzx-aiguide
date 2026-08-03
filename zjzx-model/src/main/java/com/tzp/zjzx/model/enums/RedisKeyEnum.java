package com.tzp.zjzx.model.enums;

/**
 * RedisKeyEnum 枚举类，统一管理 Redis 的 key 格式
 */
public enum RedisKeyEnum {

    USER_VALIDATE("user:validate");

    private final String keyPrefix;

    RedisKeyEnum(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * 根据传入的参数生成实际的 Redis key
     *
     * @param params 参数
     * @return 实际的 Redis key
     */
    public String getKey(String params) {
        return String.format(this.keyPrefix + params);
    }
}
