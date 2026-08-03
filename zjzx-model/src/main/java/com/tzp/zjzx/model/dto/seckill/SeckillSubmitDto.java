package com.tzp.zjzx.model.dto.seckill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SeckillSubmitDto {

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId不能超过64个字符")
    private String requestId;

    @NotNull(message = "收货地址不能为空")
    private Long userAddressId;
}

