package com.tzp.zjzx.model.enums;

import lombok.Getter;

@Getter
public enum InventoryReservationStatus {

    RESERVED(0),
    CONFIRMED(1),
    RELEASED(2);

    private final int code;

    InventoryReservationStatus(int code) {
        this.code = code;
    }
}
