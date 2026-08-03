package com.tzp.zjzx.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SeckillActivityStatus {

    DRAFT(0),
    PREHEATING(1),
    PUBLISHED(2),
    ENDING(3),
    ENDED(4);

    private final int code;
}

