package com.tzp.zjzx.model.entity.seckill;

import com.tzp.zjzx.model.entity.base.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeckillSku extends BaseEntity {

    private Long activityId;
    private Long skuId;
    private BigDecimal seckillPrice;
    private Integer totalStock;
    private Integer availableStock;
    private Integer limitPerUser;
    private Integer status;
}

