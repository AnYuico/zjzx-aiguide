package com.tzp.zjzx.model.event.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSucceededEvent {

    private String eventId;
    private String orderNo;
    private String tradeNo;
    private Integer payType;
    private BigDecimal amount;
    private Date paidAt;
}
