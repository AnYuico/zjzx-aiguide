package com.tzp.zjzx.agent.client;

import com.tzp.zjzx.ai.contract.dto.AgentOrderCancellationCandidateDto;
import com.tzp.zjzx.ai.contract.vo.AgentCartItemVo;
import com.tzp.zjzx.ai.contract.vo.AgentCartMutationResultVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderCancellationResultVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderSummaryVo;
import com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PersonalDataClient {

    Mono<AgentUserPrincipalVo> resolvePrincipal(String mallToken);

    Mono<List<AgentCartItemVo>> getCart(Long userId);

    Mono<List<AgentOrderSummaryVo>> listRecentOrders(
            Long userId,
            String status,
            int limit);

    Mono<AgentCartMutationResultVo> addCartItem(
            Long userId,
            String requestId,
            Long skuId,
            int quantity);

    Mono<AgentOrderCancellationCandidateDto> getCancellationCandidate(
            Long userId,
            int recentPosition);

    Mono<AgentOrderCancellationResultVo> cancelOrder(
            Long userId,
            String requestId,
            String orderNo);
}
