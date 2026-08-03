package com.tzp.zjzx.agent.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record GuideSearchRequest(
        @Size(max = 50, message = "商品关键词不能超过 50 个字符")
        String keyword,

        @Min(value = 1, message = "返回商品数量不能小于 1")
        @Max(value = 20, message = "返回商品数量不能大于 20")
        Integer limit
) {
}
