package com.tzp.zjzx.manager.listener;

import com.tzp.zjzx.manager.service.OrderStatisticsMqService;
import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.consume.MqMessageParser;
import com.tzp.zjzx.model.event.order.OrderPaidEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderStatisticsMqListener {

    private final MqMessageParser messageParser;
    private final OrderStatisticsMqService statisticsMqService;

    public OrderStatisticsMqListener(MqMessageParser messageParser,
                                     OrderStatisticsMqService statisticsMqService) {
        this.messageParser = messageParser;
        this.statisticsMqService = statisticsMqService;
    }

    @RabbitListener(queues = RabbitMqConstants.MANAGER_ORDER_PAID_QUEUE)
    public void onOrderPaid(Message message) {
        statisticsMqService.recordPaidOrder(
                messageParser.read(message, OrderPaidEvent.class));
    }
}
