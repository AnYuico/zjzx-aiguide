package com.tzp.zjzx.order.service;

import com.tzp.zjzx.mq.MqEventIds;
import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.consume.MqConsumeLogRepository;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import com.tzp.zjzx.model.entity.order.OrderInfo;
import com.tzp.zjzx.model.entity.order.OrderLog;
import com.tzp.zjzx.model.enums.InventoryOperationType;
import com.tzp.zjzx.model.enums.OrderStatus;
import com.tzp.zjzx.model.event.order.InventoryOperationCompletedEvent;
import com.tzp.zjzx.model.event.order.InventoryOperationEvent;
import com.tzp.zjzx.model.event.order.OrderPaidEvent;
import com.tzp.zjzx.model.event.order.OrderTimeoutEvent;
import com.tzp.zjzx.model.event.order.PaymentSucceededEvent;
import com.tzp.zjzx.order.mapper.InventoryOperationTaskMapper;
import com.tzp.zjzx.order.mapper.OrderInfoMapper;
import com.tzp.zjzx.order.mapper.OrderLogMapper;
import com.tzp.zjzx.order.mapper.PaymentExceptionTaskMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;

@Service
public class OrderMqEventService {

    private static final String LATE_PAYMENT = "LATE_PAYMENT";
    private static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    private static final String TIMEOUT_REASON = "Payment timeout";

    private final OrderInfoMapper orderInfoMapper;
    private final OrderLogMapper orderLogMapper;
    private final InventoryOperationTaskMapper inventoryTaskMapper;
    private final PaymentExceptionTaskMapper paymentExceptionTaskMapper;
    private final MqConsumeLogRepository consumeLogRepository;
    private final MqOutboxService outboxService;
    private final SeckillStockReturnService seckillStockReturnService;

    @Value("${zjzx.order.inventory-fallback-delay-ms:120000}")
    private long inventoryFallbackDelayMillis;

    public OrderMqEventService(OrderInfoMapper orderInfoMapper,
                               OrderLogMapper orderLogMapper,
                               InventoryOperationTaskMapper inventoryTaskMapper,
                               PaymentExceptionTaskMapper paymentExceptionTaskMapper,
                               MqConsumeLogRepository consumeLogRepository,
                               MqOutboxService outboxService,
                               SeckillStockReturnService seckillStockReturnService) {
        this.orderInfoMapper = orderInfoMapper;
        this.orderLogMapper = orderLogMapper;
        this.inventoryTaskMapper = inventoryTaskMapper;
        this.paymentExceptionTaskMapper = paymentExceptionTaskMapper;
        this.consumeLogRepository = consumeLogRepository;
        this.outboxService = outboxService;
        this.seckillStockReturnService = seckillStockReturnService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        validatePaymentEvent(event);
        if (!consumeLogRepository.tryClaim(RabbitMqConstants.ORDER_PAYMENT_CONSUMER,
                event.getEventId(), RabbitMqConstants.PAYMENT_SUCCEEDED_EVENT)) {
            return;
        }

        OrderInfo order = requireOrder(event.getOrderNo());
        if (order.getTotalAmount() == null
                || order.getTotalAmount().compareTo(event.getAmount()) != 0) {
            paymentExceptionTaskMapper.insertIgnore(event, AMOUNT_MISMATCH);
            return;
        }

        int updated = orderInfoMapper.markPaid(event.getOrderNo(),
                event.getPayType(), event.getPaidAt());
        OrderInfo current = requireOrder(event.getOrderNo());
        if (updated == 0) {
            if (Integer.valueOf(OrderStatus.CANCELLED.getCode()).equals(current.getOrderStatus())) {
                paymentExceptionTaskMapper.insertIgnore(event, LATE_PAYMENT);
                return;
            }
            if (current.getOrderStatus() == null
                    || current.getOrderStatus() < OrderStatus.WAITING_DELIVERY.getCode()) {
                throw new IllegalStateException("Order payment transition failed: " + event.getOrderNo());
            }
        } else {
            saveOrderLog(current.getId(), OrderStatus.WAITING_DELIVERY.getCode(),
                    "Alipay payment succeeded");
        }

        enqueueInventoryOperation(current, InventoryOperationType.CONFIRM);
        OrderPaidEvent paidEvent = new OrderPaidEvent(
                MqEventIds.orderPaid(current.getOrderNo()),
                current.getOrderNo(),
                current.getTotalAmount(),
                event.getPaidAt());
        outboxService.enqueue(paidEvent.getEventId(),
                RabbitMqConstants.ORDER_PAID_EVENT,
                RabbitMqConstants.ORDER_EVENT_EXCHANGE,
                RabbitMqConstants.ORDER_PAID,
                paidEvent);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleTimeout(OrderTimeoutEvent event) {
        validateTimeoutEvent(event);
        if (!consumeLogRepository.tryClaim(RabbitMqConstants.ORDER_TIMEOUT_CONSUMER,
                event.getEventId(), RabbitMqConstants.ORDER_TIMEOUT_EVENT)) {
            return;
        }

        Date now = new Date();
        int updated = orderInfoMapper.closeExpired(event.getOrderNo(), now, TIMEOUT_REASON);
        OrderInfo order = requireOrder(event.getOrderNo());
        if (updated == 0) {
            if (Integer.valueOf(OrderStatus.WAITING_PAYMENT.getCode()).equals(order.getOrderStatus())
                    && order.getExpireTime() != null && order.getExpireTime().after(now)) {
                throw new IllegalStateException("Order timeout event arrived early: " + event.getOrderNo());
            }
            return;
        }

        saveOrderLog(order.getId(), OrderStatus.CANCELLED.getCode(), "Order payment timed out");
        enqueueInventoryOperation(order, InventoryOperationType.RELEASE);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleInventoryCompleted(InventoryOperationCompletedEvent event) {
        if (event == null || !StringUtils.hasText(event.getEventId())
                || !StringUtils.hasText(event.getOrderNo())) {
            throw new IllegalArgumentException("Invalid inventory completion event");
        }
        InventoryOperationType operationType =
                InventoryOperationType.fromCode(event.getOperationType());
        if (!consumeLogRepository.tryClaim(
                RabbitMqConstants.ORDER_INVENTORY_COMPLETED_CONSUMER,
                event.getEventId(), RabbitMqConstants.INVENTORY_COMPLETED_EVENT)) {
            return;
        }
        inventoryTaskMapper.markSuccess(event.getOrderNo(), event.getOperationType());
        if (operationType == InventoryOperationType.RELEASE) {
            seckillStockReturnService.returnAfterPhysicalRelease(event.getOrderNo());
        }
    }

    public void enqueueInventoryOperation(OrderInfo order, InventoryOperationType operationType) {
        Date fallbackTime = new Date(System.currentTimeMillis() + inventoryFallbackDelayMillis);
        inventoryTaskMapper.insertIgnore(order.getOrderNo(), operationType.getCode(), fallbackTime);

        String eventId = operationType == InventoryOperationType.CONFIRM
                ? MqEventIds.inventoryConfirm(order.getOrderNo())
                : MqEventIds.inventoryRelease(order.getOrderNo());
        String eventType = operationType == InventoryOperationType.CONFIRM
                ? RabbitMqConstants.INVENTORY_CONFIRM_EVENT
                : RabbitMqConstants.INVENTORY_RELEASE_EVENT;
        String routingKey = operationType == InventoryOperationType.CONFIRM
                ? RabbitMqConstants.INVENTORY_CONFIRM_REQUESTED
                : RabbitMqConstants.INVENTORY_RELEASE_REQUESTED;
        InventoryOperationEvent operationEvent = new InventoryOperationEvent(
                eventId, order.getOrderNo(), operationType.getCode());
        outboxService.enqueue(eventId, eventType,
                RabbitMqConstants.ORDER_EVENT_EXCHANGE, routingKey, operationEvent);
    }

    private OrderInfo requireOrder(String orderNo) {
        OrderInfo order = orderInfoMapper.getByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalStateException("Order does not exist: " + orderNo);
        }
        return order;
    }

    private void saveOrderLog(Long orderId, int status, String note) {
        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderId);
        orderLog.setProcessStatus(status);
        orderLog.setNote(note);
        orderLogMapper.save(orderLog);
    }

    private void validatePaymentEvent(PaymentSucceededEvent event) {
        if (event == null || !StringUtils.hasText(event.getEventId())
                || !StringUtils.hasText(event.getOrderNo())
                || event.getAmount() == null
                || event.getAmount().compareTo(BigDecimal.ZERO) < 0
                || event.getPaidAt() == null) {
            throw new IllegalArgumentException("Invalid payment succeeded event");
        }
    }

    private void validateTimeoutEvent(OrderTimeoutEvent event) {
        if (event == null || !StringUtils.hasText(event.getEventId())
                || !StringUtils.hasText(event.getOrderNo()) || event.getExpireAt() == null) {
            throw new IllegalArgumentException("Invalid order timeout event");
        }
    }
}
