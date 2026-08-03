package com.tzp.zjzx.model.vo.seckill;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeckillSkuAdminVo {

    private Long id;
    private Long skuId;
    private String skuName;
    private String thumbImg;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer totalStock;
    private Integer availableStock;
    private Integer limitPerUser;
    private Integer status;
}
