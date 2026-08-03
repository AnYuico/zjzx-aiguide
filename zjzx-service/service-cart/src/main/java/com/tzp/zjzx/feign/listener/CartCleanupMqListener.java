package com.tzp.zjzx.feign.listener;

import com.tzp.zjzx.feign.service.CartCleanupMqService;
import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.consume.MqMessageParser;
import com.tzp.zjzx.model.event.cart.CartCleanupRequestedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CartCleanupMqListener {

    private final MqMessageParser messageParser;
    private final CartCleanupMqService cartCleanupMqService;

    public CartCleanupMqListener(MqMessageParser messageParser,
                                 CartCleanupMqService cartCleanupMqService) {
        this.messageParser = messageParser;
        this.cartCleanupMqService = cartCleanupMqService;
    }

    @RabbitListener(queues = RabbitMqConstants.CART_CLEANUP_QUEUE)
    public void onCartCleanup(Message message) {
        cartCleanupMqService.cleanup(
                messageParser.read(message, CartCleanupRequestedEvent.class));
    }
}
