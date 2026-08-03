package com.tzp.zjzx.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderStatus {

    CANCELLED(-1),
    WAITING_PAYMENT(0),
    WAITING_DELIVERY(1),
    DELIVERED(2),
    COMPLETED(3);

    private final int code;
}
