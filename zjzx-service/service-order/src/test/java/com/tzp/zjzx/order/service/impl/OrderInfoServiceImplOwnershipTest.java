package com.tzp.zjzx.order.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.ai.contract.dto.AgentOrderCancelRequestDto;
import com.tzp.zjzx.ai.contract.dto.AgentOrderCancellationCandidateDto;
import com.tzp.zjzx.ai.contract.vo.AgentOrderCancellationResultVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderSummaryVo;
import com.tzp.zjzx.model.dto.internal.OrderPaymentInternalDto;
import com.tzp.zjzx.model.entity.order.OrderInfo;
import com.tzp.zjzx.model.entity.order.OrderItem;
import com.tzp.zjzx.model.entity.order.OrderLog;
import com.tzp.zjzx.model.entity.user.UserInfo;
import com.tzp.zjzx.model.enums.InventoryOperationType;
import com.tzp.zjzx.model.enums.OrderStatus;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.order.OrderDetailVo;
import com.tzp.zjzx.order.mapper.OrderInfoMapper;
import com.tzp.zjzx.order.mapper.OrderItemMapper;
import com.tzp.zjzx.order.mapper.OrderLogMapper;
import com.tzp.zjzx.order.exception.AgentOrderActionException;
import com.tzp.zjzx.order.service.OrderMqEventService;
import com.tzp.zjzx.utils.AuthContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderInfoServiceImplOwnershipTest {

    @Mock
    private OrderInfoMapper orderInfoMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private OrderLogMapper orderLogMapper;

    @Mock
    private OrderMqEventService orderMqEventService;

    @InjectMocks
    private OrderInfoServiceImpl orderInfoService;

    @BeforeEach
    void setCurrentUser() {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(11L);
        AuthContextUtil.setUserInfo(userInfo);
    }

    @AfterEach
    void clearUserContext() {
        AuthContextUtil.removeUserInfo();
    }

    @Test
    void orderIdLookupUsesCurrentUserId() {
        OrderInfo expected = new OrderInfo();
        expected.setId(31L);
        when(orderInfoMapper.getByIdAndUserId(31L, 11L)).thenReturn(expected);
        when(orderItemMapper.findByOrderId(31L)).thenReturn(List.of());

        OrderDetailVo actual = orderInfoService.getOrderInfo(31L);

        assertEquals(31L, actual.getId());
        verify(orderInfoMapper).getByIdAndUserId(31L, 11L);
    }

    @Test
    void publicOrderNumberLookupUsesCurrentUserId() {
        OrderInfo expected = new OrderInfo();
        expected.setId(31L);
        List<OrderItem> items = List.of(new OrderItem());
        when(orderInfoMapper.getByOrderNoAndUserId("order-31", 11L)).thenReturn(expected);
        when(orderItemMapper.findByOrderId(31L)).thenReturn(items);

        OrderDetailVo actual = orderInfoService.getByOrderNo("order-31");

        assertEquals(31L, actual.getId());
        assertEquals(1, actual.getOrderItemList().size());
        verify(orderInfoMapper).getByOrderNoAndUserId("order-31", 11L);
    }

    @Test
    void internalOrderLookupUsesProtectedUnscopedQuery() {
        OrderInfo expected = new OrderInfo();
        expected.setId(31L);
        expected.setUserId(11L);
        expected.setOrderNo("order-31");
        when(orderInfoMapper.getByOrderNo("order-31")).thenReturn(expected);
        when(orderItemMapper.findByOrderId(31L)).thenReturn(List.of());

        OrderPaymentInternalDto actual = orderInfoService.getByOrderNoInternal("order-31");

        assertEquals(11L, actual.getUserId());
        assertEquals("order-31", actual.getOrderNo());
        verify(orderInfoMapper).getByOrderNo("order-31");
        verify(orderInfoMapper, never()).getByOrderNoAndUserId("order-31", 11L);
    }

    @Test
    void internalOrderLookupReturnsControlledNotFoundError() {
        when(orderInfoMapper.getByOrderNo("missing-order")).thenReturn(null);

        MyException exception = assertThrows(
                MyException.class,
                () -> orderInfoService.getByOrderNoInternal("missing-order")
        );

        assertEquals(ResultCodeEnum.ORDER_NOT_FOUND, exception.getResultCodeEnum());
        verify(orderItemMapper, never()).findByOrderId(any());
    }

    @Test
    void publicLookupRejectsMissingUserContext() {
        AuthContextUtil.removeUserInfo();

        assertThrows(MyException.class, () -> orderInfoService.getOrderInfo(31L));
        verify(orderInfoMapper, never()).getByIdAndUserId(31L, 11L);
    }

    @Test
    void cancelPendingOrderUsesOwnershipAndRequestsInventoryRelease() {
        OrderInfo cancelled = order("order-41", OrderStatus.CANCELLED.getCode());
        when(orderInfoMapper.cancelPendingByUser(
                eq("order-41"), eq(11L), any(), eq("Cancelled by user"))).thenReturn(1);
        when(orderInfoMapper.getByOrderNoAndUserId("order-41", 11L)).thenReturn(cancelled);

        orderInfoService.cancelOrder("order-41");

        ArgumentCaptor<OrderLog> logCaptor = ArgumentCaptor.forClass(OrderLog.class);
        verify(orderLogMapper).save(logCaptor.capture());
        assertEquals(41L, logCaptor.getValue().getOrderId());
        assertEquals("USER:11", logCaptor.getValue().getOperateUser());
        assertEquals(OrderStatus.CANCELLED.getCode(), logCaptor.getValue().getProcessStatus());
        verify(orderMqEventService).enqueueInventoryOperation(
                cancelled, InventoryOperationType.RELEASE);
    }

    @Test
    void cancellingAlreadyCancelledOrderIsIdempotent() {
        OrderInfo cancelled = order("order-42", OrderStatus.CANCELLED.getCode());
        when(orderInfoMapper.cancelPendingByUser(
                eq("order-42"), eq(11L), any(), eq("Cancelled by user"))).thenReturn(0);
        when(orderInfoMapper.getByOrderNoAndUserId("order-42", 11L)).thenReturn(cancelled);

        orderInfoService.cancelOrder("order-42");

        verify(orderLogMapper, never()).save(any());
        verify(orderMqEventService, never()).enqueueInventoryOperation(any(), any());
    }

    @Test
    void cancellingPaidOrderIsRejected() {
        OrderInfo paid = order("order-43", OrderStatus.WAITING_DELIVERY.getCode());
        when(orderInfoMapper.cancelPendingByUser(
                eq("order-43"), eq(11L), any(), eq("Cancelled by user"))).thenReturn(0);
        when(orderInfoMapper.getByOrderNoAndUserId("order-43", 11L)).thenReturn(paid);

        MyException exception = assertThrows(
                MyException.class, () -> orderInfoService.cancelOrder("order-43"));

        assertEquals(ResultCodeEnum.ORDER_CANNOT_CANCEL, exception.getResultCodeEnum());
        verify(orderMqEventService, never()).enqueueInventoryOperation(any(), any());
    }

    @Test
    void cancellingUnknownOrForeignOrderDoesNotRevealOwnership() {
        when(orderInfoMapper.cancelPendingByUser(
                eq("order-44"), eq(11L), any(), eq("Cancelled by user"))).thenReturn(0);
        when(orderInfoMapper.getByOrderNoAndUserId("order-44", 11L)).thenReturn(null);

        MyException exception = assertThrows(
                MyException.class, () -> orderInfoService.cancelOrder("order-44"));

        assertEquals(ResultCodeEnum.ORDER_NOT_FOUND, exception.getResultCodeEnum());
        verify(orderMqEventService, never()).enqueueInventoryOperation(any(), any());
    }

    @Test
    void deleteCancelledOrderHidesItForCurrentUser() {
        OrderInfo cancelled = order("order-51", OrderStatus.CANCELLED.getCode());
        when(orderInfoMapper.getByOrderNoAndUserIdIncludingDeleted("order-51", 11L))
                .thenReturn(cancelled);
        when(orderInfoMapper.hideByUser(
                "order-51", 11L,
                OrderStatus.CANCELLED.getCode(), OrderStatus.COMPLETED.getCode()))
                .thenReturn(1);

        orderInfoService.deleteOrder("order-51");

        verify(orderInfoMapper).hideByUser(
                "order-51", 11L,
                OrderStatus.CANCELLED.getCode(), OrderStatus.COMPLETED.getCode());
        ArgumentCaptor<OrderLog> logCaptor = ArgumentCaptor.forClass(OrderLog.class);
        verify(orderLogMapper).save(logCaptor.capture());
        assertEquals(51L, logCaptor.getValue().getOrderId());
        assertEquals("Hidden by user", logCaptor.getValue().getNote());
    }

    @Test
    void deleteCompletedOrderIsAllowed() {
        OrderInfo completed = order("order-52", OrderStatus.COMPLETED.getCode());
        when(orderInfoMapper.getByOrderNoAndUserIdIncludingDeleted("order-52", 11L))
                .thenReturn(completed);
        when(orderInfoMapper.hideByUser(
                "order-52", 11L,
                OrderStatus.CANCELLED.getCode(), OrderStatus.COMPLETED.getCode()))
                .thenReturn(1);

        orderInfoService.deleteOrder("order-52");

        verify(orderInfoMapper).hideByUser(
                "order-52", 11L,
                OrderStatus.CANCELLED.getCode(), OrderStatus.COMPLETED.getCode());
    }

    @Test
    void deletePendingOrderIsRejected() {
        OrderInfo pending = order("order-53", OrderStatus.WAITING_PAYMENT.getCode());
        when(orderInfoMapper.getByOrderNoAndUserIdIncludingDeleted("order-53", 11L))
                .thenReturn(pending);

        MyException exception = assertThrows(
                MyException.class, () -> orderInfoService.deleteOrder("order-53"));

        assertEquals(ResultCodeEnum.ORDER_CANNOT_DELETE, exception.getResultCodeEnum());
        verify(orderInfoMapper, never()).hideByUser(any(), any(), any(), any());
        verify(orderLogMapper, never()).save(any());
    }

    @Test
    void deletingAlreadyHiddenOrderIsIdempotent() {
        OrderInfo hidden = order("order-54", OrderStatus.CANCELLED.getCode());
        hidden.setUserDeleted(1);
        when(orderInfoMapper.getByOrderNoAndUserIdIncludingDeleted("order-54", 11L))
                .thenReturn(hidden);

        orderInfoService.deleteOrder("order-54");

        verify(orderInfoMapper, never()).hideByUser(any(), any(), any(), any());
        verify(orderLogMapper, never()).save(any());
    }

    @Test
    void deletingUnknownOrForeignOrderDoesNotRevealOwnership() {
        when(orderInfoMapper.getByOrderNoAndUserIdIncludingDeleted("order-55", 11L))
                .thenReturn(null);

        MyException exception = assertThrows(
                MyException.class, () -> orderInfoService.deleteOrder("order-55"));

        assertEquals(ResultCodeEnum.ORDER_NOT_FOUND, exception.getResultCodeEnum());
        verify(orderInfoMapper, never()).hideByUser(any(), any(), any(), any());
    }

    @Test
    void agentRecentOrdersStayBoundToUserAndExcludeSensitiveDetails() {
        OrderInfo order = order("order-61", OrderStatus.WAITING_PAYMENT.getCode());
        order.setUserId(33L);
        order.setTotalAmount(new BigDecimal("1999.00"));
        order.setCreateTime(new Date(0));
        order.setReceiverPhone("13800000000");
        order.setReceiverAddress("sensitive-address");
        OrderItem item = new OrderItem();
        item.setSkuName("Mac mini 16G");
        when(orderInfoMapper.findAgentRecentOrders(
                33L,
                OrderStatus.WAITING_PAYMENT.getCode(),
                5
        )).thenReturn(List.of(order));
        when(orderItemMapper.findByOrderId(61L)).thenReturn(List.of(item));

        List<AgentOrderSummaryVo> result =
                orderInfoService.findAgentRecentOrders(
                        33L,
                        "WAITING_PAYMENT",
                        5
                );

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getRecentPosition());
        assertEquals("WAITING_PAYMENT", result.get(0).getStatus());
        assertEquals(List.of("Mac mini 16G"), result.get(0).getProductNames());
        verify(orderInfoMapper).findAgentRecentOrders(
                33L,
                OrderStatus.WAITING_PAYMENT.getCode(),
                5
        );
    }

    @Test
    void agentCancellationCandidateResolvesWaitingOrderByPosition() {
        OrderInfo newest = order(
                "order-61",
                OrderStatus.WAITING_PAYMENT.getCode()
        );
        newest.setTotalAmount(new BigDecimal("1999.00"));
        newest.setCreateTime(new Date(0));
        OrderItem item = new OrderItem();
        item.setSkuName("Mac mini 16G");
        when(orderInfoMapper.findAgentRecentOrders(
                33L,
                OrderStatus.WAITING_PAYMENT.getCode(),
                1
        )).thenReturn(List.of(newest));
        when(orderItemMapper.findByOrderId(61L)).thenReturn(List.of(item));

        AgentOrderCancellationCandidateDto candidate =
                orderInfoService.findAgentCancellationCandidate(33L, 1);

        assertEquals(1, candidate.getRecentPosition());
        assertEquals("order-61", candidate.getOrderNo());
        assertEquals(List.of("Mac mini 16G"), candidate.getProductNames());
        verify(orderInfoMapper).findAgentRecentOrders(
                33L,
                OrderStatus.WAITING_PAYMENT.getCode(),
                1
        );
    }

    @Test
    void agentCancellationUsesOwnershipConditionAndReleaseOutbox() {
        AgentOrderCancelRequestDto request = agentCancelRequest("order-62");
        OrderInfo cancelled = order(
                "order-62",
                OrderStatus.CANCELLED.getCode()
        );
        when(orderInfoMapper.cancelPendingByUser(
                eq("order-62"),
                eq(33L),
                any(),
                eq("Cancelled by shopping guide after user confirmation")
        )).thenReturn(1);
        when(orderInfoMapper.getByOrderNoAndUserId("order-62", 33L))
                .thenReturn(cancelled);

        AgentOrderCancellationResultVo result =
                orderInfoService.cancelAgentOrder(33L, request);

        assertEquals(true, result.getApplied());
        assertEquals(false, result.getReplayed());
        ArgumentCaptor<OrderLog> logCaptor =
                ArgumentCaptor.forClass(OrderLog.class);
        verify(orderLogMapper).save(logCaptor.capture());
        assertEquals("AGENT:33", logCaptor.getValue().getOperateUser());
        verify(orderMqEventService).enqueueInventoryOperation(
                cancelled,
                InventoryOperationType.RELEASE
        );
    }

    @Test
    void repeatedAgentCancellationDoesNotPublishReleaseTwice() {
        AgentOrderCancelRequestDto request = agentCancelRequest("order-63");
        OrderInfo cancelled = order(
                "order-63",
                OrderStatus.CANCELLED.getCode()
        );
        when(orderInfoMapper.cancelPendingByUser(
                eq("order-63"),
                eq(33L),
                any(),
                eq("Cancelled by shopping guide after user confirmation")
        )).thenReturn(0);
        when(orderInfoMapper.getByOrderNoAndUserId("order-63", 33L))
                .thenReturn(cancelled);

        AgentOrderCancellationResultVo result =
                orderInfoService.cancelAgentOrder(33L, request);

        assertEquals(false, result.getApplied());
        assertEquals(true, result.getReplayed());
        verify(orderLogMapper, never()).save(any());
        verify(orderMqEventService, never())
                .enqueueInventoryOperation(any(), any());
    }

    @Test
    void agentCancellationRejectsOrderPaidBeforeConfirmation() {
        AgentOrderCancelRequestDto request = agentCancelRequest("order-64");
        OrderInfo paid = order(
                "order-64",
                OrderStatus.WAITING_DELIVERY.getCode()
        );
        when(orderInfoMapper.cancelPendingByUser(
                eq("order-64"),
                eq(33L),
                any(),
                eq("Cancelled by shopping guide after user confirmation")
        )).thenReturn(0);
        when(orderInfoMapper.getByOrderNoAndUserId("order-64", 33L))
                .thenReturn(paid);

        AgentOrderActionException exception = assertThrows(
                AgentOrderActionException.class,
                () -> orderInfoService.cancelAgentOrder(33L, request)
        );

        assertEquals(
                AgentOrderActionException.Reason.CONFLICT,
                exception.getReason()
        );
        verify(orderMqEventService, never())
                .enqueueInventoryOperation(any(), any());
    }

    @Test
    void agentCancellationHidesForeignOrderExistence() {
        AgentOrderCancelRequestDto request = agentCancelRequest("order-65");
        when(orderInfoMapper.cancelPendingByUser(
                eq("order-65"),
                eq(33L),
                any(),
                eq("Cancelled by shopping guide after user confirmation")
        )).thenReturn(0);
        when(orderInfoMapper.getByOrderNoAndUserId("order-65", 33L))
                .thenReturn(null);

        AgentOrderActionException exception = assertThrows(
                AgentOrderActionException.class,
                () -> orderInfoService.cancelAgentOrder(33L, request)
        );

        assertEquals(
                AgentOrderActionException.Reason.NOT_FOUND,
                exception.getReason()
        );
    }

    private AgentOrderCancelRequestDto agentCancelRequest(String orderNo) {
        AgentOrderCancelRequestDto request = new AgentOrderCancelRequestDto();
        request.setRequestId("d0b2abec-b950-4a6f-94f6-8f54647d2db6");
        request.setOrderNo(orderNo);
        return request;
    }

    private OrderInfo order(String orderNo, int status) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(Long.parseLong(orderNo.substring(orderNo.length() - 2)));
        orderInfo.setOrderNo(orderNo);
        orderInfo.setOrderStatus(status);
        orderInfo.setUserDeleted(0);
        return orderInfo;
    }
}
