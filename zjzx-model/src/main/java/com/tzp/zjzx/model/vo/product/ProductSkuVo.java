package com.tzp.zjzx.model.vo.product;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductSkuVo {

    private Long id;
    private String skuCode;
    private String skuName;
    private Long productId;
    private String thumbImg;
    private BigDecimal salePrice;
    private BigDecimal marketPrice;
    private Integer stockNum;
    private Integer saleNum;
    private String skuSpec;
    private BigDecimal weight;
    private BigDecimal volume;
    private Integer status;
}
