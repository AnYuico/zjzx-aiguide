package com.tzp.zjzx.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SeckillSkuStatus {

    DRAFT(0),
    ACTIVE(1),
    ENDED(2);

    private final int code;
}

