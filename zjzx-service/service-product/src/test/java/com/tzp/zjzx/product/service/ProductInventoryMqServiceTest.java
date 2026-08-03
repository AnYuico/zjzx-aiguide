package com.tzp.zjzx.product.service;

import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.consume.MqConsumeLogRepository;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import com.tzp.zjzx.model.enums.InventoryOperationType;
import com.tzp.zjzx.model.event.order.InventoryOperationEvent;
import com.tzp.zjzx.product.service.impl.InventoryTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductInventoryMqServiceTest {

    @Mock
    private InventoryTransactionService inventoryTransactionService;
    @Mock
    private MqConsumeLogRepository consumeLogRepository;
    @Mock
    private MqOutboxService outboxService;

    private ProductInventoryMqService service;

    @BeforeEach
    void setUp() {
        service = new ProductInventoryMqService(
                inventoryTransactionService, consumeLogRepository, outboxService);
    }

    @Test
    void confirmExecutesInventoryTransactionAndPublishesCompletion() {
        InventoryOperationEvent event = new InventoryOperationEvent(
                "inventory.confirm:order-1", "order-1",
                InventoryOperationType.CONFIRM.getCode());
        when(consumeLogRepository.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);

        service.confirm(event);

        verify(inventoryTransactionService).confirmStock("order-1");
        verify(outboxService).enqueue(anyString(),
                eq(RabbitMqConstants.INVENTORY_COMPLETED_EVENT),
                eq(RabbitMqConstants.ORDER_EVENT_EXCHANGE),
                eq(RabbitMqConstants.INVENTORY_OPERATION_COMPLETED), any());
    }

    @Test
    void duplicateInventoryEventDoesNotExecuteAgain() {
        InventoryOperationEvent event = new InventoryOperationEvent(
                "inventory.release:order-2", "order-2",
                InventoryOperationType.RELEASE.getCode());
        when(consumeLogRepository.tryClaim(anyString(), anyString(), anyString())).thenReturn(false);

        service.release(event);

        verify(inventoryTransactionService, never()).releaseStock(anyString());
        verify(outboxService, never()).enqueue(anyString(), anyString(), anyString(), anyString(), any());
    }
}
