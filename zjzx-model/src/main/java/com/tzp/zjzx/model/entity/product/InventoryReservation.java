package com.tzp.zjzx.model.entity.product;

import com.tzp.zjzx.model.entity.base.BaseEntity;
import lombok.Data;

@Data
public class InventoryReservation extends BaseEntity {

    private String orderNo;

    private Long skuId;

    private Integer skuNum;

    private Integer status;
}
