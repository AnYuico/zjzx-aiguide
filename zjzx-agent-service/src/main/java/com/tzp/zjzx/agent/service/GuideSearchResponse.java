package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;

import java.util.List;

public record GuideSearchResponse(
        String keyword,
        String message,
        int count,
        List<ProductGuideVo> products
) {
}
