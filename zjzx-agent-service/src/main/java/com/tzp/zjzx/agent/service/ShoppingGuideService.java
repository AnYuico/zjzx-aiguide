package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class ShoppingGuideService {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 20;
    static final int MAX_KEYWORD_LENGTH = 50;

    private final HybridProductRetrievalService productRetrievalService;

    public ShoppingGuideService(HybridProductRetrievalService productRetrievalService) {
        this.productRetrievalService = productRetrievalService;
    }

    public Mono<GuideSearchResponse> search(String rawKeyword, Integer requestedLimit) {
        return Mono.defer(() -> {
            String keyword = normalizeKeyword(rawKeyword);
            int limit = normalizeLimit(requestedLimit);

            return productRetrievalService.search(keyword, limit)
                    .map(products -> toResponse(keyword, products));
        });
    }

    private GuideSearchResponse toResponse(String keyword, List<ProductGuideVo> products) {
        List<ProductGuideVo> safeProducts = products == null ? List.of() : List.copyOf(products);
        String message;
        if (safeProducts.isEmpty()) {
            message = "暂未找到符合条件的商品，请尝试更换关键词。";
        } else if (keyword == null) {
            message = "已为你展示当前可选商品。";
        } else {
            message = "已找到 " + safeProducts.size() + " 个相关商品。";
        }
        return new GuideSearchResponse(keyword, message, safeProducts.size(), safeProducts);
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("商品关键词不能超过 50 个字符");
        }
        return normalized;
    }

    private int normalizeLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("返回商品数量必须在 1 到 20 之间");
        }
        return limit;
    }
}
