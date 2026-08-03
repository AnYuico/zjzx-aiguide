package com.tzp.zjzx.model.enums;

import lombok.Getter;

@Getter
public enum InventoryRequestStatus {

    PROCESSING(0),
    RESERVED(1),
    CONFIRMED(2),
    RELEASED(3);

    private final int code;

    InventoryRequestStatus(int code) {
        this.code = code;
    }
}
