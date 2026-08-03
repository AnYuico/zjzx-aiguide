package com.tzp.zjzx.agent.action;

import java.math.BigDecimal;
import java.util.List;

public record CancelRecentOrderActionPayload(
        String orderNo,
        Integer recentPosition,
        BigDecimal totalAmount,
        String createdAt,
        List<String> productNames) {

    public CancelRecentOrderActionPayload {
        productNames = productNames == null
                ? List.of()
                : List.copyOf(productNames);
    }
}
