package com.tzp.zjzx.product.service;

import com.tzp.zjzx.mq.MqEventIds;
import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.consume.MqConsumeLogRepository;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import com.tzp.zjzx.model.enums.InventoryOperationType;
import com.tzp.zjzx.model.event.order.InventoryOperationCompletedEvent;
import com.tzp.zjzx.model.event.order.InventoryOperationEvent;
import com.tzp.zjzx.product.service.impl.InventoryTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;

@Service
public class ProductInventoryMqService {

    private final InventoryTransactionService inventoryTransactionService;
    private final MqConsumeLogRepository consumeLogRepository;
    private final MqOutboxService outboxService;

    public ProductInventoryMqService(InventoryTransactionService inventoryTransactionService,
                                     MqConsumeLogRepository consumeLogRepository,
                                     MqOutboxService outboxService) {
        this.inventoryTransactionService = inventoryTransactionService;
        this.consumeLogRepository = consumeLogRepository;
        this.outboxService = outboxService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirm(InventoryOperationEvent event) {
        handle(event, InventoryOperationType.CONFIRM,
                RabbitMqConstants.PRODUCT_INVENTORY_CONFIRM_CONSUMER,
                RabbitMqConstants.INVENTORY_CONFIRM_EVENT);
    }

    @Transactional(rollbackFor = Exception.class)
    public void release(InventoryOperationEvent event) {
        handle(event, InventoryOperationType.RELEASE,
                RabbitMqConstants.PRODUCT_INVENTORY_RELEASE_CONSUMER,
                RabbitMqConstants.INVENTORY_RELEASE_EVENT);
    }

    private void handle(InventoryOperationEvent event,
                        InventoryOperationType expectedOperation,
                        String consumerName,
                        String eventType) {
        validate(event, expectedOperation);
        if (!consumeLogRepository.tryClaim(consumerName, event.getEventId(), eventType)) {
            return;
        }

        if (expectedOperation == InventoryOperationType.CONFIRM) {
            inventoryTransactionService.confirmStock(event.getOrderNo());
        } else {
            inventoryTransactionService.releaseStock(event.getOrderNo());
        }

        InventoryOperationCompletedEvent completedEvent =
                new InventoryOperationCompletedEvent(
                        MqEventIds.inventoryCompleted(
                                event.getOrderNo(), expectedOperation.getCode()),
                        event.getOrderNo(), expectedOperation.getCode(), new Date());
        outboxService.enqueue(completedEvent.getEventId(),
                RabbitMqConstants.INVENTORY_COMPLETED_EVENT,
                RabbitMqConstants.ORDER_EVENT_EXCHANGE,
                RabbitMqConstants.INVENTORY_OPERATION_COMPLETED,
                completedEvent);
    }

    private void validate(InventoryOperationEvent event,
                          InventoryOperationType expectedOperation) {
        if (event == null || !StringUtils.hasText(event.getEventId())
                || !StringUtils.hasText(event.getOrderNo())
                || !Integer.valueOf(expectedOperation.getCode()).equals(event.getOperationType())) {
            throw new IllegalArgumentException("Invalid inventory operation event");
        }
    }
}
