package com.tzp.zjzx.agent.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuideChatRequest(
        @NotBlank(message = "导购问题不能为空")
        @Size(max = 500, message = "导购问题不能超过 500 个字符")
        String message,

        @Min(value = 1, message = "返回商品数量不能小于 1")
        @Max(value = 20, message = "返回商品数量不能大于 20")
        Integer limit
) {
}
