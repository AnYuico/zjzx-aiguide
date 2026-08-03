package com.tzp.zjzx.model.dto.internal;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemInternalDto {

    private Long skuId;
    private BigDecimal cartPrice;
    private Integer skuNum;
    private String imgUrl;
    private String skuName;
}
