package com.tzp.zjzx.order.service;

import com.tzp.zjzx.model.enums.InventoryOperationType;
import com.tzp.zjzx.order.mapper.InventoryOperationTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockReleaseCompensationServiceTest {

    @Mock
    private InventoryOperationTaskMapper taskMapper;

    @Test
    void recordsFailedReleaseAsUnifiedInventoryOperation() {
        StockReleaseCompensationService service =
                new StockReleaseCompensationService(taskMapper);

        service.record("order-1");

        verify(taskMapper).insertIgnore(eq("order-1"),
                eq(InventoryOperationType.RELEASE.getCode()), any());
    }
}
