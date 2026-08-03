package com.tzp.zjzx.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SeckillRequestStatus {

    QUEUED(0),
    PROCESSING(1),
    SUCCESS(2),
    FAILED(3),
    CANCELLED(4);

    private final int code;
}

