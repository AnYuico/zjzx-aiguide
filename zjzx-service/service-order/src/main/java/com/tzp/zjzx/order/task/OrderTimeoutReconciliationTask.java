package com.tzp.zjzx.order.task;

import com.tzp.zjzx.mq.MqEventIds;
import com.tzp.zjzx.model.entity.order.OrderInfo;
import com.tzp.zjzx.model.event.order.OrderTimeoutEvent;
import com.tzp.zjzx.order.mapper.OrderInfoMapper;
import com.tzp.zjzx.order.service.OrderMqEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class OrderTimeoutReconciliationTask {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderMqEventService eventService;

    public OrderTimeoutReconciliationTask(OrderInfoMapper orderInfoMapper,
                                          OrderMqEventService eventService) {
        this.orderInfoMapper = orderInfoMapper;
        this.eventService = eventService;
    }

    @Scheduled(fixedDelayString = "${zjzx.order.timeout-scan-delay-ms:60000}",
            initialDelayString = "${zjzx.order.timeout-scan-initial-delay-ms:60000}")
    public void closeExpiredOrders() {
        List<OrderInfo> expiredOrders = orderInfoMapper.findExpiredOrders(100);
        for (OrderInfo order : expiredOrders) {
            try {
                eventService.handleTimeout(new OrderTimeoutEvent(
                        MqEventIds.orderTimeout(order.getOrderNo()),
                        order.getOrderNo(), order.getExpireTime()));
            } catch (RuntimeException ex) {
                log.warn("Timeout reconciliation failed for order {}", order.getOrderNo(), ex);
            }
        }
    }
}
