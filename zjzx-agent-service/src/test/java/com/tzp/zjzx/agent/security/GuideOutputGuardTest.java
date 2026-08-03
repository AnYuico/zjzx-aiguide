package com.tzp.zjzx.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideOutputGuardTest {

    private final GuideOutputGuard guard = new GuideOutputGuard();

    @Test
    void acceptsOrdinaryProductRecommendation() {
        assertTrue(guard.isSafe(
                "建议选择 Mac mini，当前售价 4999 元，库存以商品页面为准。"
        ));
    }

    @Test
    void rejectsSecretPiiAndWriteOperationClaims() {
        assertFalse(guard.isSafe("api_key=sk-1234567890abcdef"));
        assertFalse(guard.isSafe("请联系买家 13800138000"));
        assertFalse(guard.isSafe("已为您下单，稍后可以支付"));
        assertFalse(guard.isSafe(
                "订单号 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        ));
        assertFalse(guard.isSafe("已为您取消订单，可以重新选购。"));
    }

    @Test
    void acceptsExplicitlyPendingCancellationWording() {
        assertTrue(guard.isSafe(
                "已准备取消最近的待付款订单，请在界面确认后执行。"
        ));
    }
}
