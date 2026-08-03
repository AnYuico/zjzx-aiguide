package com.tzp.zjzx.product.listener;

import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.consume.MqMessageParser;
import com.tzp.zjzx.model.event.order.InventoryOperationEvent;
import com.tzp.zjzx.product.service.ProductInventoryMqService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProductInventoryMqListener {

    private final MqMessageParser messageParser;
    private final ProductInventoryMqService inventoryMqService;

    public ProductInventoryMqListener(MqMessageParser messageParser,
                                      ProductInventoryMqService inventoryMqService) {
        this.messageParser = messageParser;
        this.inventoryMqService = inventoryMqService;
    }

    @RabbitListener(queues = RabbitMqConstants.PRODUCT_INVENTORY_CONFIRM_QUEUE)
    public void onConfirm(Message message) {
        inventoryMqService.confirm(messageParser.read(message, InventoryOperationEvent.class));
    }

    @RabbitListener(queues = RabbitMqConstants.PRODUCT_INVENTORY_RELEASE_QUEUE)
    public void onRelease(Message message) {
        inventoryMqService.release(messageParser.read(message, InventoryOperationEvent.class));
    }
}
