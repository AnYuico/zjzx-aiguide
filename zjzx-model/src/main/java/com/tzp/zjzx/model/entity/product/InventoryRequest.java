package com.tzp.zjzx.model.entity.product;

import com.tzp.zjzx.model.entity.base.BaseEntity;
import lombok.Data;

@Data
public class InventoryRequest extends BaseEntity {

    private String orderNo;

    private String itemsHash;

    private Integer status;
}
