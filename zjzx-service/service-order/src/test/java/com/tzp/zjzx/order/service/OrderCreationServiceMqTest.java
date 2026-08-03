package com.tzp.zjzx.order.service;

import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import com.tzp.zjzx.model.entity.order.OrderInfo;
import com.tzp.zjzx.model.entity.order.OrderItem;
import com.tzp.zjzx.model.enums.OrderSource;
import com.tzp.zjzx.model.event.cart.CartCleanupRequestedEvent;
import com.tzp.zjzx.model.event.order.OrderTimeoutEvent;
import com.tzp.zjzx.order.mapper.OrderInfoMapper;
import com.tzp.zjzx.order.mapper.OrderItemMapper;
import com.tzp.zjzx.order.mapper.OrderLogMapper;
import com.tzp.zjzx.order.mapper.OrderSubmitRequestMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreationServiceMqTest {

    @Mock
    private OrderInfoMapper orderInfoMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderLogMapper orderLogMapper;
    @Mock
    private OrderSubmitRequestMapper submitRequestMapper;
    @Mock
    private MqOutboxService outboxService;

    @Test
    void orderAndTimeoutEventAreCreatedTogether() {
        OrderCreationService service = new OrderCreationService(orderInfoMapper,
                orderItemMapper, orderLogMapper, submitRequestMapper, outboxService);
        OrderInfo order = new OrderInfo();
        order.setId(10L);
        order.setOrderNo("order-10");
        order.setRequestId("request-10");
        order.setUserId(20L);
        order.setOrderSource(OrderSource.CART.getCode());
        order.setExpireTime(new Date(System.currentTimeMillis() + 1800000L));
        when(submitRequestMapper.markSuccess("request-10", 20L, 10L)).thenReturn(1);
        OrderItem orderItem = new OrderItem();
        orderItem.setSkuId(1001L);
        orderItem.setSkuNum(2);

        service.createOrder(order, List.of(orderItem));

        ArgumentCaptor<OrderTimeoutEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderTimeoutEvent.class);
        verify(outboxService).enqueue(anyString(),
                eq(RabbitMqConstants.ORDER_TIMEOUT_EVENT),
                eq(RabbitMqConstants.ORDER_EVENT_EXCHANGE),
                eq(RabbitMqConstants.ORDER_TIMEOUT_DELAY),
                eventCaptor.capture(), eq(order.getExpireTime()));
        assertEquals("order-10", eventCaptor.getValue().getOrderNo());
        ArgumentCaptor<CartCleanupRequestedEvent> cleanupCaptor =
                ArgumentCaptor.forClass(CartCleanupRequestedEvent.class);
        verify(outboxService).enqueue(anyString(),
                eq(RabbitMqConstants.CART_CLEANUP_EVENT),
                eq(RabbitMqConstants.ORDER_EVENT_EXCHANGE),
                eq(RabbitMqConstants.CART_CLEANUP_REQUESTED),
                cleanupCaptor.capture());
        assertEquals(20L, cleanupCaptor.getValue().getUserId());
        assertEquals(1001L, cleanupCaptor.getValue().getItems().get(0).getSkuId());
        assertEquals(2, cleanupCaptor.getValue().getItems().get(0).getSkuNum());
        verify(orderInfoMapper).save(order);
        verify(orderItemMapper).save(any(OrderItem.class));
    }

    @Test
    void buyNowOrderDoesNotCreateCartCleanupEvent() {
        OrderCreationService service = new OrderCreationService(orderInfoMapper,
                orderItemMapper, orderLogMapper, submitRequestMapper, outboxService);
        OrderInfo order = new OrderInfo();
        order.setId(11L);
        order.setOrderNo("order-11");
        order.setRequestId("request-11");
        order.setUserId(20L);
        order.setOrderSource(OrderSource.BUY_NOW.getCode());
        order.setExpireTime(new Date(System.currentTimeMillis() + 1800000L));
        when(submitRequestMapper.markSuccess("request-11", 20L, 11L)).thenReturn(1);

        service.createOrder(order, List.of(new OrderItem()));

        verify(outboxService, never()).enqueue(anyString(),
                eq(RabbitMqConstants.CART_CLEANUP_EVENT),
                anyString(), anyString(), any(CartCleanupRequestedEvent.class));
    }
}
