package com.tzp.zjzx.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InventoryOperationType {

    CONFIRM(1),
    RELEASE(2);

    private final int code;

    public static InventoryOperationType fromCode(Integer code) {
        for (InventoryOperationType value : values()) {
            if (code != null && value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown inventory operation type: " + code);
    }
}
