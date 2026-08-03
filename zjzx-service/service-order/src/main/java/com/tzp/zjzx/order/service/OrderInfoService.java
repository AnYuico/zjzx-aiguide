package com.tzp.zjzx.order.service;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.ai.contract.dto.AgentOrderCancelRequestDto;
import com.tzp.zjzx.ai.contract.dto.AgentOrderCancellationCandidateDto;
import com.tzp.zjzx.ai.contract.vo.AgentOrderCancellationResultVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderSummaryVo;
import com.tzp.zjzx.model.dto.h5.OrderInfoDto;
import com.tzp.zjzx.model.dto.internal.OrderPaymentInternalDto;
import com.tzp.zjzx.model.vo.h5.TradeVo;
import com.tzp.zjzx.model.vo.order.OrderDetailVo;

import java.util.List;

public interface OrderInfoService {

    TradeVo getTrade();

    Long submitOrder(OrderInfoDto orderInfoDto);

    OrderDetailVo getOrderInfo(Long orderId);

    TradeVo buy(Long skuId);

    PageInfo<OrderDetailVo> findUserPage(Integer page, Integer limit, Integer orderStatus);

    OrderDetailVo getByOrderNo(String orderNo);

    void cancelOrder(String orderNo);

    void deleteOrder(String orderNo);

    OrderPaymentInternalDto getByOrderNoInternal(String orderNo);

    void updateOrderStatus(String orderNo, Integer orderStatus);

    List<AgentOrderSummaryVo> findAgentRecentOrders(
            Long userId,
            String status,
            Integer limit);

    AgentOrderCancellationCandidateDto findAgentCancellationCandidate(
            Long userId,
            Integer recentPosition);

    AgentOrderCancellationResultVo cancelAgentOrder(
            Long userId,
            AgentOrderCancelRequestDto request);
}
