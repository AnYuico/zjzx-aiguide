package com.tzp.zjzx.model.event.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryOperationEvent {

    private String eventId;
    private String orderNo;
    private Integer operationType;
}
