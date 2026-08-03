package com.tzp.zjzx.order.listener;

import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.consume.MqMessageParser;
import com.tzp.zjzx.model.event.order.InventoryOperationCompletedEvent;
import com.tzp.zjzx.model.event.order.OrderTimeoutEvent;
import com.tzp.zjzx.model.event.order.PaymentSucceededEvent;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.order.service.OrderMqEventService;
import com.tzp.zjzx.order.service.SeckillOrderConsumerService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderMqListener {

    private final MqMessageParser messageParser;
    private final OrderMqEventService eventService;
    private final SeckillOrderConsumerService seckillOrderConsumerService;

    public OrderMqListener(MqMessageParser messageParser,
                           OrderMqEventService eventService,
                           SeckillOrderConsumerService seckillOrderConsumerService) {
        this.messageParser = messageParser;
        this.eventService = eventService;
        this.seckillOrderConsumerService = seckillOrderConsumerService;
    }

    @RabbitListener(queues = RabbitMqConstants.ORDER_PAYMENT_QUEUE)
    public void onPaymentSucceeded(Message message) {
        eventService.handlePaymentSucceeded(
                messageParser.read(message, PaymentSucceededEvent.class));
    }

    @RabbitListener(queues = RabbitMqConstants.ORDER_TIMEOUT_QUEUE)
    public void onOrderTimeout(Message message) {
        eventService.handleTimeout(messageParser.read(message, OrderTimeoutEvent.class));
    }

    @RabbitListener(queues = RabbitMqConstants.ORDER_INVENTORY_COMPLETED_QUEUE)
    public void onInventoryCompleted(Message message) {
        eventService.handleInventoryCompleted(
                messageParser.read(message, InventoryOperationCompletedEvent.class));
    }

    @RabbitListener(queues = RabbitMqConstants.SECKILL_ORDER_QUEUE)
    public void onSeckillOrderRequested(Message message) {
        seckillOrderConsumerService.process(
                messageParser.read(message, SeckillOrderRequestedEvent.class));
    }
}
