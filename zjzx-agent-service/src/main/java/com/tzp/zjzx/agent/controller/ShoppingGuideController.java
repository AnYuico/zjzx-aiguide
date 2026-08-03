package com.tzp.zjzx.agent.controller;

import com.tzp.zjzx.agent.service.GuideSearchResponse;
import com.tzp.zjzx.agent.service.ShoppingGuideService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/agent/guide")
public class ShoppingGuideController {

    private final ShoppingGuideService shoppingGuideService;

    public ShoppingGuideController(ShoppingGuideService shoppingGuideService) {
        this.shoppingGuideService = shoppingGuideService;
    }

    @PostMapping("/search")
    public Mono<GuideSearchResponse> search(@Valid @RequestBody GuideSearchRequest request) {
        return shoppingGuideService.search(request.keyword(), request.limit());
    }
}
