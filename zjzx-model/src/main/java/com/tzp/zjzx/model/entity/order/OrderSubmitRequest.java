package com.tzp.zjzx.model.entity.order;

import com.tzp.zjzx.model.entity.base.BaseEntity;
import lombok.Data;

@Data
public class OrderSubmitRequest extends BaseEntity {

    private String requestId;

    private Long userId;

    private String orderNo;

    private Integer status;

    private Long orderId;
}
