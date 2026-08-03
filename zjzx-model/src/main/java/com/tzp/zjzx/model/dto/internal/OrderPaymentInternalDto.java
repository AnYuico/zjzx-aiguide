package com.tzp.zjzx.model.dto.internal;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderPaymentInternalDto {

    private Long userId;
    private String orderNo;
    private BigDecimal totalAmount;
    private Integer payType;
    private Integer orderStatus;
    private List<String> skuNames;
}
