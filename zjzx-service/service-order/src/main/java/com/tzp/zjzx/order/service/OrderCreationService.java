package com.tzp.zjzx.order.service;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.mq.MqEventIds;
import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import com.tzp.zjzx.model.entity.order.OrderInfo;
import com.tzp.zjzx.model.entity.order.OrderItem;
import com.tzp.zjzx.model.entity.order.OrderLog;
import com.tzp.zjzx.model.enums.OrderSource;
import com.tzp.zjzx.model.event.cart.CartCleanupItemEvent;
import com.tzp.zjzx.model.event.cart.CartCleanupRequestedEvent;
import com.tzp.zjzx.model.event.order.OrderTimeoutEvent;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.order.mapper.OrderInfoMapper;
import com.tzp.zjzx.order.mapper.OrderItemMapper;
import com.tzp.zjzx.order.mapper.OrderLogMapper;
import com.tzp.zjzx.order.mapper.OrderSubmitRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderCreationService {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderLogMapper orderLogMapper;
    private final OrderSubmitRequestMapper requestMapper;
    private final MqOutboxService mqOutboxService;

    public OrderCreationService(OrderInfoMapper orderInfoMapper,
                                OrderItemMapper orderItemMapper,
                                OrderLogMapper orderLogMapper,
                                OrderSubmitRequestMapper requestMapper,
                                MqOutboxService mqOutboxService) {
        this.orderInfoMapper = orderInfoMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderLogMapper = orderLogMapper;
        this.requestMapper = requestMapper;
        this.mqOutboxService = mqOutboxService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderInfo orderInfo, List<OrderItem> orderItems) {
        orderInfoMapper.save(orderInfo);
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrderId(orderInfo.getId());
            orderItemMapper.save(orderItem);
        }

        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderInfo.getId());
        orderLog.setProcessStatus(0);
        orderLog.setNote("提交订单并预占库存");
        orderLogMapper.save(orderLog);

        int updated = requestMapper.markSuccess(
                orderInfo.getRequestId(), orderInfo.getUserId(), orderInfo.getId());
        if (updated != 1) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }

        OrderTimeoutEvent timeoutEvent = new OrderTimeoutEvent(
                MqEventIds.orderTimeout(orderInfo.getOrderNo()),
                orderInfo.getOrderNo(),
                orderInfo.getExpireTime());
        mqOutboxService.enqueue(timeoutEvent.getEventId(),
                RabbitMqConstants.ORDER_TIMEOUT_EVENT,
                RabbitMqConstants.ORDER_EVENT_EXCHANGE,
                RabbitMqConstants.ORDER_TIMEOUT_DELAY,
                timeoutEvent,
                orderInfo.getExpireTime());

        if (Integer.valueOf(OrderSource.CART.getCode()).equals(orderInfo.getOrderSource())) {
            List<CartCleanupItemEvent> cleanupItems = orderItems.stream()
                    .map(item -> new CartCleanupItemEvent(item.getSkuId(), item.getSkuNum()))
                    .collect(Collectors.toList());
            CartCleanupRequestedEvent cleanupEvent = new CartCleanupRequestedEvent(
                    MqEventIds.cartCleanup(orderInfo.getOrderNo()),
                    orderInfo.getOrderNo(),
                    orderInfo.getUserId(),
                    orderInfo.getOrderSource(),
                    cleanupItems,
                    new Date());
            mqOutboxService.enqueue(cleanupEvent.getEventId(),
                    RabbitMqConstants.CART_CLEANUP_EVENT,
                    RabbitMqConstants.ORDER_EVENT_EXCHANGE,
                    RabbitMqConstants.CART_CLEANUP_REQUESTED,
                    cleanupEvent);
        }
        return orderInfo.getId();
    }
}
