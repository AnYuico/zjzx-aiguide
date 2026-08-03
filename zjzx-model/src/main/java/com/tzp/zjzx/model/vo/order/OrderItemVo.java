package com.tzp.zjzx.model.vo.order;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemVo {

    private Long id;
    private Long skuId;
    private String skuName;
    private String thumbImg;
    private BigDecimal skuPrice;
    private Integer skuNum;
}
