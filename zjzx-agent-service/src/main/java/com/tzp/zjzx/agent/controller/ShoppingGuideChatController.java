package com.tzp.zjzx.agent.controller;

import com.tzp.zjzx.agent.service.GuideChatResponse;
import com.tzp.zjzx.agent.service.ShoppingGuideChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/agent/auth/guide")
public class ShoppingGuideChatController {

    private final ShoppingGuideChatService shoppingGuideChatService;

    public ShoppingGuideChatController(ShoppingGuideChatService shoppingGuideChatService) {
        this.shoppingGuideChatService = shoppingGuideChatService;
    }

    @PostMapping("/chat")
    public Mono<GuideChatResponse> chat(
            @RequestHeader(value = "token", required = false) String mallToken,
            @Valid @RequestBody GuideChatRequest request) {
        return shoppingGuideChatService.chat(
                mallToken,
                request.message(),
                request.limit()
        );
    }
}
