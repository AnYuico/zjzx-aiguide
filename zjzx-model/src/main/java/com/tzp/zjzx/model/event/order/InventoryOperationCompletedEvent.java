package com.tzp.zjzx.model.event.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryOperationCompletedEvent {

    private String eventId;
    private String orderNo;
    private Integer operationType;
    private Date completedAt;
}
