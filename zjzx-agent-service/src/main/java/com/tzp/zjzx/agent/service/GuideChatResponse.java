package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.ai.contract.vo.AgentActionPreparationVo;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;

import java.util.List;

public record GuideChatResponse(
        String answer,
        GuideResponseMode mode,
        String model,
        List<ProductGuideVo> products,
        List<AgentActionPreparationVo> pendingActions
) {

    public GuideChatResponse(
            String answer,
            GuideResponseMode mode,
            String model,
            List<ProductGuideVo> products) {
        this(answer, mode, model, products, List.of());
    }

    public GuideChatResponse {
        products = products == null ? List.of() : List.copyOf(products);
        pendingActions = pendingActions == null
                ? List.of()
                : List.copyOf(pendingActions);
    }

    public enum GuideResponseMode {
        AI,
        DETERMINISTIC_FALLBACK
    }
}
