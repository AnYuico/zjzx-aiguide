package com.tzp.zjzx.feign.controller;

import com.tzp.zjzx.ai.contract.dto.AgentCartAddRequestDto;
import com.tzp.zjzx.ai.contract.vo.AgentCartItemVo;
import com.tzp.zjzx.ai.contract.vo.AgentCartMutationResultVo;
import com.tzp.zjzx.common.security.InternalApiAuth;
import com.tzp.zjzx.feign.exception.AgentCartMutationException;
import com.tzp.zjzx.feign.service.AgentCartMutationService;
import com.tzp.zjzx.feign.service.CartService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/order/cart/internal/agent")
public class AgentCartInternalController {

    private final CartService cartService;
    private final AgentCartMutationService mutationService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    public AgentCartInternalController(
            CartService cartService,
            AgentCartMutationService mutationService) {
        this.cartService = cartService;
        this.mutationService = mutationService;
    }

    @GetMapping("/users/{userId}")
    public List<AgentCartItemVo> getCurrentCart(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String internalToken,
            @PathVariable Long userId) {
        InternalApiAuth.verify(internalApiToken, internalToken);
        return cartService.getAgentCart(userId);
    }

    @PostMapping("/users/{userId}/items")
    public AgentCartMutationResultVo addItem(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String internalToken,
            @PathVariable Long userId,
            @RequestBody AgentCartAddRequestDto request) {
        InternalApiAuth.verify(internalApiToken, internalToken);
        return mutationService.addItem(userId, request);
    }

    @ExceptionHandler(AgentCartMutationException.class)
    public ResponseEntity<MutationError> handleMutationError(
            AgentCartMutationException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status)
                .body(new MutationError(exception.getReason().name()));
    }

    public record MutationError(String code) {
    }
}
