package com.tzp.zjzx.utils;

public final class SeckillRedisKeys {

    public static final String ACTIVE_SKUS = "seckill:active-skus";

    private SeckillRedisKeys() {
    }

    public static String member(Long activityId, Long skuId) {
        return activityId + ":" + skuId;
    }

    public static String meta(Long activityId, Long skuId) {
        return base(activityId, skuId) + "meta";
    }

    public static String stock(Long activityId, Long skuId) {
        return base(activityId, skuId) + "stock";
    }

    public static String buyers(Long activityId, Long skuId) {
        return base(activityId, skuId) + "buyers";
    }

    public static String payloads(Long activityId, Long skuId) {
        return base(activityId, skuId) + "payloads";
    }

    public static String results(Long activityId, Long skuId) {
        return base(activityId, skuId) + "results";
    }

    public static String pending(Long activityId, Long skuId) {
        return base(activityId, skuId) + "pending";
    }

    public static String attempts(Long activityId, Long skuId) {
        return base(activityId, skuId) + "attempts";
    }

    public static String rollbacks(Long activityId, Long skuId) {
        return base(activityId, skuId) + "rollbacks";
    }

    public static String returns(Long activityId, Long skuId) {
        return base(activityId, skuId) + "returns";
    }

    private static String base(Long activityId, Long skuId) {
        return "seckill:{" + member(activityId, skuId) + "}:";
    }
}
