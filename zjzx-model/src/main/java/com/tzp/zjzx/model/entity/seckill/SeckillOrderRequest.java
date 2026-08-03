package com.tzp.zjzx.model.entity.seckill;

import com.tzp.zjzx.model.entity.base.BaseEntity;
import lombok.Data;

import java.util.Date;

@Data
public class SeckillOrderRequest extends BaseEntity {

    private String requestId;
    private Long activityId;
    private Long seckillSkuId;
    private Long skuId;
    private Long userId;
    private Long userAddressId;
    private String orderNo;
    private Long orderId;
    private Integer status;
    private Integer retryCount;
    private Date nextRetryTime;
    private String failReason;
    private Integer stockReturned;
}

