package com.tzp.zjzx.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderSource {

    CART(1),
    BUY_NOW(2),
    SECKILL(3);

    private final int code;

    public static OrderSource fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderSource source : values()) {
            if (source.code == code) {
                return source;
            }
        }
        return null;
    }
}
