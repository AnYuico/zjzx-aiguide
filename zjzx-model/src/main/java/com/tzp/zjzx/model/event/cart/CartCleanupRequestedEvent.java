package com.tzp.zjzx.model.event.cart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartCleanupRequestedEvent {

    private String eventId;
    private String orderNo;
    private Long userId;
    private Integer orderSource;
    private List<CartCleanupItemEvent> items;
    private Date occurredAt;
}
