package com.tzp.zjzx.order.service;

import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.consume.MqConsumeLogRepository;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import com.tzp.zjzx.model.entity.order.OrderInfo;
import com.tzp.zjzx.model.enums.InventoryOperationType;
import com.tzp.zjzx.model.enums.OrderStatus;
import com.tzp.zjzx.model.event.order.OrderTimeoutEvent;
import com.tzp.zjzx.model.event.order.PaymentSucceededEvent;
import com.tzp.zjzx.order.mapper.InventoryOperationTaskMapper;
import com.tzp.zjzx.order.mapper.OrderInfoMapper;
import com.tzp.zjzx.order.mapper.OrderLogMapper;
import com.tzp.zjzx.order.mapper.PaymentExceptionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMqEventServiceTest {

    @Mock
    private OrderInfoMapper orderInfoMapper;
    @Mock
    private OrderLogMapper orderLogMapper;
    @Mock
    private InventoryOperationTaskMapper inventoryTaskMapper;
    @Mock
    private PaymentExceptionTaskMapper paymentExceptionTaskMapper;
    @Mock
    private MqConsumeLogRepository consumeLogRepository;
    @Mock
    private MqOutboxService outboxService;
    @Mock
    private SeckillStockReturnService seckillStockReturnService;

    private OrderMqEventService service;

    @BeforeEach
    void setUp() {
        service = new OrderMqEventService(orderInfoMapper, orderLogMapper,
                inventoryTaskMapper, paymentExceptionTaskMapper,
                consumeLogRepository, outboxService, seckillStockReturnService);
        ReflectionTestUtils.setField(service, "inventoryFallbackDelayMillis", 120000L);
    }

    @Test
    void paymentEventMarksOrderPaidAndCreatesDownstreamEvents() {
        PaymentSucceededEvent event = paymentEvent("order-1");
        OrderInfo unpaid = order("order-1", OrderStatus.WAITING_PAYMENT.getCode());
        OrderInfo paid = order("order-1", OrderStatus.WAITING_DELIVERY.getCode());
        when(consumeLogRepository.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(orderInfoMapper.getByOrderNo("order-1")).thenReturn(unpaid, paid);
        when(orderInfoMapper.markPaid(eq("order-1"), eq(2), any())).thenReturn(1);

        service.handlePaymentSucceeded(event);

        verify(inventoryTaskMapper).insertIgnore(eq("order-1"),
                eq(InventoryOperationType.CONFIRM.getCode()), any());
        verify(outboxService).enqueue(anyString(),
                eq(RabbitMqConstants.INVENTORY_CONFIRM_EVENT),
                eq(RabbitMqConstants.ORDER_EVENT_EXCHANGE),
                eq(RabbitMqConstants.INVENTORY_CONFIRM_REQUESTED), any());
        verify(outboxService).enqueue(anyString(),
                eq(RabbitMqConstants.ORDER_PAID_EVENT),
                eq(RabbitMqConstants.ORDER_EVENT_EXCHANGE),
                eq(RabbitMqConstants.ORDER_PAID), any());
    }

    @Test
    void timeoutClosesOnlyUnpaidOrderAndRequestsRelease() {
        Date expiredAt = new Date(System.currentTimeMillis() - 1000L);
        OrderTimeoutEvent event = new OrderTimeoutEvent(
                "order.timeout:order-2", "order-2", expiredAt);
        OrderInfo cancelled = order("order-2", OrderStatus.CANCELLED.getCode());
        cancelled.setExpireTime(expiredAt);
        when(consumeLogRepository.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(orderInfoMapper.closeExpired(eq("order-2"), any(), anyString())).thenReturn(1);
        when(orderInfoMapper.getByOrderNo("order-2")).thenReturn(cancelled);

        service.handleTimeout(event);

        verify(inventoryTaskMapper).insertIgnore(eq("order-2"),
                eq(InventoryOperationType.RELEASE.getCode()), any());
        verify(outboxService).enqueue(anyString(),
                eq(RabbitMqConstants.INVENTORY_RELEASE_EVENT),
                eq(RabbitMqConstants.ORDER_EVENT_EXCHANGE),
                eq(RabbitMqConstants.INVENTORY_RELEASE_REQUESTED), any());
    }

    @Test
    void paymentAfterTimeoutCreatesExceptionTaskWithoutConfirmingStock() {
        PaymentSucceededEvent event = paymentEvent("order-3");
        OrderInfo cancelled = order("order-3", OrderStatus.CANCELLED.getCode());
        when(consumeLogRepository.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(orderInfoMapper.getByOrderNo("order-3")).thenReturn(cancelled, cancelled);
        when(orderInfoMapper.markPaid(eq("order-3"), eq(2), any())).thenReturn(0);

        service.handlePaymentSucceeded(event);

        verify(paymentExceptionTaskMapper).insertIgnore(event, "LATE_PAYMENT");
        verify(inventoryTaskMapper, never()).insertIgnore(anyString(), anyInt(), any());
        verify(outboxService, never()).enqueue(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void duplicatePaymentEventDoesNothing() {
        PaymentSucceededEvent event = paymentEvent("order-4");
        when(consumeLogRepository.tryClaim(anyString(), anyString(), anyString())).thenReturn(false);

        service.handlePaymentSucceeded(event);

        verify(orderInfoMapper, never()).getByOrderNo(anyString());
        verify(outboxService, never()).enqueue(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void completedReleaseAlsoReturnsSeckillActivityStock() {
        when(consumeLogRepository.tryClaim(anyString(), anyString(), anyString()))
                .thenReturn(true);

        service.handleInventoryCompleted(
                new com.tzp.zjzx.model.event.order.InventoryOperationCompletedEvent(
                        "inventory.release.completed:order-5",
                        "order-5", InventoryOperationType.RELEASE.getCode(),
                        new Date()));

        verify(inventoryTaskMapper).markSuccess(
                "order-5", InventoryOperationType.RELEASE.getCode());
        verify(seckillStockReturnService).returnAfterPhysicalRelease("order-5");
    }

    private PaymentSucceededEvent paymentEvent(String orderNo) {
        return new PaymentSucceededEvent("payment.succeeded:" + orderNo,
                orderNo, "trade-1", 2, new BigDecimal("25.00"), new Date());
    }

    private OrderInfo order(String orderNo, int status) {
        OrderInfo order = new OrderInfo();
        order.setId(1L);
        order.setOrderNo(orderNo);
        order.setOrderStatus(status);
        order.setTotalAmount(new BigDecimal("25.00"));
        return order;
    }
}
