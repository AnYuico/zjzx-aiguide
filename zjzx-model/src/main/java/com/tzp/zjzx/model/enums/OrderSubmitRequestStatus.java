package com.tzp.zjzx.model.enums;

import lombok.Getter;

@Getter
public enum OrderSubmitRequestStatus {

    PROCESSING(0),
    SUCCESS(1),
    FAILED(2);

    private final int code;

    OrderSubmitRequestStatus(int code) {
        this.code = code;
    }
}
