package com.tzp.zjzx.order.service;

import com.tzp.zjzx.model.enums.InventoryOperationType;
import com.tzp.zjzx.order.mapper.InventoryOperationTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class StockReleaseCompensationService {

    private final InventoryOperationTaskMapper taskMapper;

    public StockReleaseCompensationService(InventoryOperationTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String orderNo) {
        taskMapper.insertIgnore(orderNo, InventoryOperationType.RELEASE.getCode(), new Date());
    }
}
