package com.tzp.zjzx.model.event.cart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartCleanupItemEvent {

    private Long skuId;
    private Integer skuNum;
}
