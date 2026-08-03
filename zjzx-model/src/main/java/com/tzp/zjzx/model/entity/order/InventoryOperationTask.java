package com.tzp.zjzx.model.entity.order;

import com.tzp.zjzx.model.entity.base.BaseEntity;
import lombok.Data;

import java.util.Date;

@Data
public class InventoryOperationTask extends BaseEntity {

    private String orderNo;
    private Integer operationType;
    private Integer status;
    private Integer retryCount;
    private Date nextRetryTime;
    private String lastError;
}
