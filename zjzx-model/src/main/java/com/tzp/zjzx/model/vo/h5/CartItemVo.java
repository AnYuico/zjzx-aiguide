package com.tzp.zjzx.model.vo.h5;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemVo {

    private Long skuId;
    private BigDecimal cartPrice;
    private Integer skuNum;
    private String imgUrl;
    private String skuName;
    private Integer isChecked;
}
