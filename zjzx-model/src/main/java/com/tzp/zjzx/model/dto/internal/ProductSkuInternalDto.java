package com.tzp.zjzx.model.dto.internal;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductSkuInternalDto {

    private Long id;
    private String skuName;
    private String thumbImg;
    private BigDecimal salePrice;
    private Integer status;
    private Integer isDeleted;
}
