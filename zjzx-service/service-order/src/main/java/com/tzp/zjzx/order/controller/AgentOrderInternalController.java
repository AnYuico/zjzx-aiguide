package com.tzp.zjzx.order.controller;

import com.tzp.zjzx.ai.contract.dto.AgentOrderCancelRequestDto;
import com.tzp.zjzx.ai.contract.dto.AgentOrderCancellationCandidateDto;
import com.tzp.zjzx.ai.contract.vo.AgentOrderCancellationResultVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderSummaryVo;
import com.tzp.zjzx.common.security.InternalApiAuth;
import com.tzp.zjzx.order.exception.AgentOrderActionException;
import com.tzp.zjzx.order.service.OrderInfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/order/orderInfo/internal/agent")
public class AgentOrderInternalController {

    private final OrderInfoService orderInfoService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    public AgentOrderInternalController(OrderInfoService orderInfoService) {
        this.orderInfoService = orderInfoService;
    }

    @GetMapping("/users/{userId}/recent")
    public List<AgentOrderSummaryVo> getRecentOrders(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String internalToken,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "5") Integer limit) {
        InternalApiAuth.verify(internalApiToken, internalToken);
        return orderInfoService.findAgentRecentOrders(userId, status, limit);
    }

    @GetMapping("/users/{userId}/cancellation-candidates/{recentPosition}")
    public AgentOrderCancellationCandidateDto getCancellationCandidate(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String internalToken,
            @PathVariable Long userId,
            @PathVariable Integer recentPosition) {
        InternalApiAuth.verify(internalApiToken, internalToken);
        return orderInfoService.findAgentCancellationCandidate(
                userId,
                recentPosition
        );
    }

    @PostMapping("/users/{userId}/cancellations")
    public AgentOrderCancellationResultVo cancelOrder(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String internalToken,
            @PathVariable Long userId,
            @RequestBody AgentOrderCancelRequestDto request) {
        InternalApiAuth.verify(internalApiToken, internalToken);
        return orderInfoService.cancelAgentOrder(userId, request);
    }

    @ExceptionHandler(AgentOrderActionException.class)
    public ResponseEntity<ActionError> handleActionError(
            AgentOrderActionException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(new ActionError(exception.getReason().name()));
    }

    public record ActionError(String code) {
    }
}
