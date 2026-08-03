package com.tzp.zjzx.pay.service.impl;

import com.tzp.zjzx.feign.order.OrderFeignClient;
import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import com.tzp.zjzx.model.dto.internal.OrderPaymentInternalDto;
import com.tzp.zjzx.model.entity.pay.PaymentInfo;
import com.tzp.zjzx.model.enums.OrderStatus;
import com.tzp.zjzx.model.event.order.PaymentSucceededEvent;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.pay.mapper.PaymentInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentInfoServiceImplMqTest {

    @Mock
    private PaymentInfoMapper paymentInfoMapper;
    @Mock
    private OrderFeignClient orderFeignClient;
    @Mock
    private MqOutboxService outboxService;
    @InjectMocks
    private PaymentInfoServiceImpl paymentInfoService;

    @BeforeEach
    void setInternalToken() {
        ReflectionTestUtils.setField(
                paymentInfoService,
                "internalApiToken",
                "internal-token"
        );
    }

    @Test
    void reusesUnpaidPaymentOnlyAfterLatestOrderValidation() {
        OrderPaymentInternalDto order =
                order("order-1", 7L, OrderStatus.WAITING_PAYMENT.getCode());
        PaymentInfo existing = payment("order-1", 0, null);
        existing.setUserId(7L);
        when(orderFeignClient.getOrderInfoByOrderNo("internal-token", "order-1"))
                .thenReturn(Result.build(order, ResultCodeEnum.SUCCESS));
        when(paymentInfoMapper.getByOrderNo("order-1")).thenReturn(existing);

        PaymentInfo actual = paymentInfoService.savePaymentInfo("order-1", 7L);

        assertSame(existing, actual);
        verify(paymentInfoMapper, never()).save(any());
    }

    @Test
    void rejectsForeignOrderWithoutReadingOrCreatingPaymentRecord() {
        OrderPaymentInternalDto order =
                order("order-2", 8L, OrderStatus.WAITING_PAYMENT.getCode());
        when(orderFeignClient.getOrderInfoByOrderNo("internal-token", "order-2"))
                .thenReturn(Result.build(order, ResultCodeEnum.SUCCESS));

        MyException exception = assertThrows(
                MyException.class,
                () -> paymentInfoService.savePaymentInfo("order-2", 7L)
        );

        assertEquals(ResultCodeEnum.ORDER_NOT_FOUND, exception.getResultCodeEnum());
        verify(paymentInfoMapper, never()).getByOrderNo(anyString());
        verify(paymentInfoMapper, never()).save(any());
    }

    @Test
    void rejectsCancelledOrPaidOrderBeforeReusingPaymentRecord() {
        OrderPaymentInternalDto order =
                order("order-3", 7L, OrderStatus.CANCELLED.getCode());
        when(orderFeignClient.getOrderInfoByOrderNo("internal-token", "order-3"))
                .thenReturn(Result.build(order, ResultCodeEnum.SUCCESS));

        MyException exception = assertThrows(
                MyException.class,
                () -> paymentInfoService.savePaymentInfo("order-3", 7L)
        );

        assertEquals(ResultCodeEnum.ORDER_CANNOT_PAY, exception.getResultCodeEnum());
        verify(paymentInfoMapper, never()).getByOrderNo(anyString());
    }

    @Test
    void rejectsPaymentRecordWhoseAmountDiffersFromLatestOrder() {
        OrderPaymentInternalDto order =
                order("order-4", 7L, OrderStatus.WAITING_PAYMENT.getCode());
        PaymentInfo existing = payment("order-4", 0, null);
        existing.setUserId(7L);
        existing.setAmount(new BigDecimal("24.99"));
        when(orderFeignClient.getOrderInfoByOrderNo("internal-token", "order-4"))
                .thenReturn(Result.build(order, ResultCodeEnum.SUCCESS));
        when(paymentInfoMapper.getByOrderNo("order-4")).thenReturn(existing);

        MyException exception = assertThrows(
                MyException.class,
                () -> paymentInfoService.savePaymentInfo("order-4", 7L)
        );

        assertEquals(ResultCodeEnum.DATA_ERROR, exception.getResultCodeEnum());
    }

    @Test
    void createsPaymentRecordFromValidatedOrderData() {
        OrderPaymentInternalDto order =
                order("order-5", 7L, OrderStatus.WAITING_PAYMENT.getCode());
        PaymentInfo stored = payment("order-5", 0, null);
        stored.setUserId(7L);
        when(orderFeignClient.getOrderInfoByOrderNo("internal-token", "order-5"))
                .thenReturn(Result.build(order, ResultCodeEnum.SUCCESS));
        when(paymentInfoMapper.getByOrderNo("order-5")).thenReturn(null, stored);
        when(paymentInfoMapper.save(any())).thenReturn(1);

        PaymentInfo actual = paymentInfoService.savePaymentInfo("order-5", 7L);

        assertSame(stored, actual);
        ArgumentCaptor<PaymentInfo> paymentCaptor =
                ArgumentCaptor.forClass(PaymentInfo.class);
        verify(paymentInfoMapper).save(paymentCaptor.capture());
        assertEquals(7L, paymentCaptor.getValue().getUserId());
        assertEquals(new BigDecimal("25.00"), paymentCaptor.getValue().getAmount());
        assertEquals("商品A 商品B", paymentCaptor.getValue().getContent());
    }

    @Test
    void paymentCallbackPersistsEventWithoutCallingOrderService() {
        PaymentInfo unpaid = payment("order-1", 0, null);
        PaymentInfo paid = payment("order-1", 1, new Date());
        paid.setOutTradeNo("trade-1");
        when(paymentInfoMapper.getByOrderNo("order-1")).thenReturn(unpaid, paid);
        when(paymentInfoMapper.markPaid(anyString(), anyString(), any(), anyString()))
                .thenReturn(1);

        paymentInfoService.updatePaymentStatus(callback("order-1", "trade-1", "25.00"));

        ArgumentCaptor<PaymentSucceededEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(outboxService).enqueue(anyString(),
                org.mockito.ArgumentMatchers.eq(RabbitMqConstants.PAYMENT_SUCCEEDED_EVENT),
                org.mockito.ArgumentMatchers.eq(RabbitMqConstants.ORDER_EVENT_EXCHANGE),
                org.mockito.ArgumentMatchers.eq(RabbitMqConstants.PAYMENT_SUCCEEDED),
                eventCaptor.capture());
        assertEquals("order-1", eventCaptor.getValue().getOrderNo());
        assertEquals(new BigDecimal("25.00"), eventCaptor.getValue().getAmount());
        verify(orderFeignClient, never()).updateOrderStatus(anyString(), anyString(), any());
    }

    private PaymentInfo payment(String orderNo, int status, Date callbackTime) {
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setUserId(7L);
        paymentInfo.setOrderNo(orderNo);
        paymentInfo.setPaymentStatus(status);
        paymentInfo.setPayType(2);
        paymentInfo.setAmount(new BigDecimal("25.00"));
        paymentInfo.setCallbackTime(callbackTime);
        return paymentInfo;
    }

    private Map<String, String> callback(String orderNo, String tradeNo, String amount) {
        Map<String, String> callback = new HashMap<>();
        callback.put("out_trade_no", orderNo);
        callback.put("trade_no", tradeNo);
        callback.put("total_amount", amount);
        return callback;
    }

    private OrderPaymentInternalDto order(
            String orderNo,
            Long userId,
            int orderStatus) {
        OrderPaymentInternalDto order = new OrderPaymentInternalDto();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setOrderStatus(orderStatus);
        order.setPayType(2);
        order.setTotalAmount(new BigDecimal("25.00"));
        order.setSkuNames(List.of("商品A", "商品B"));
        return order;
    }
}
