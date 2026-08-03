package com.tzp.zjzx.model.event.seckill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderRequestedEvent {

    private String eventId;
    private String requestId;
    private Long activityId;
    private Long seckillSkuId;
    private Long skuId;
    private Long userId;
    private Long userAddressId;
    private String orderNo;
    private BigDecimal seckillPrice;
    private Date acceptedAt;
}

