package com.tzp.zjzx.agent.action;

import java.math.BigDecimal;

public record AddToCartActionPayload(
        Long skuId,
        Integer quantity,
        String productName,
        String skuName,
        BigDecimal price,
        String imageUrl) {
}
