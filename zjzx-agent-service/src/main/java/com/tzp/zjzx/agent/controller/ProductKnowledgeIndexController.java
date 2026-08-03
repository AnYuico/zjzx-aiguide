package com.tzp.zjzx.agent.controller;

import com.tzp.zjzx.agent.config.ProductGuideProperties;
import com.tzp.zjzx.agent.service.ProductKnowledgeIndexService;
import com.tzp.zjzx.agent.service.ProductKnowledgeIndexStatus;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/agent/internal/index/products")
public class ProductKnowledgeIndexController {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final ProductKnowledgeIndexService indexService;
    private final ProductGuideProperties productGuideProperties;

    public ProductKnowledgeIndexController(
            ProductKnowledgeIndexService indexService,
            ProductGuideProperties productGuideProperties) {
        this.indexService = indexService;
        this.productGuideProperties = productGuideProperties;
    }

    @PostMapping("/rebuild")
    public Mono<ProductKnowledgeIndexStatus> rebuild(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String token) {
        verifyInternalToken(token);
        return indexService.rebuild();
    }

    @GetMapping("/status")
    public ProductKnowledgeIndexStatus status(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String token) {
        verifyInternalToken(token);
        return indexService.status();
    }

    private void verifyInternalToken(String providedToken) {
        String expectedToken = productGuideProperties.getInternalToken();
        if (!StringUtils.hasText(expectedToken)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Internal API token is not configured"
            );
        }
        if (!StringUtils.hasText(providedToken)
                || !MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
