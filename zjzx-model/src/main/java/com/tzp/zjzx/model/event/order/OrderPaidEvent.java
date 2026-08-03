package com.tzp.zjzx.model.event.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidEvent {

    private String eventId;
    private String orderNo;
    private BigDecimal totalAmount;
    private Date paidAt;
}
