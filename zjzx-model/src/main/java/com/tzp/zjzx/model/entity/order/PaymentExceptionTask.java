package com.tzp.zjzx.model.entity.order;

import com.tzp.zjzx.model.entity.base.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentExceptionTask extends BaseEntity {

    private String eventId;
    private String orderNo;
    private String tradeNo;
    private BigDecimal amount;
    private String reason;
    private Integer status;
}
