package com.tzp.zjzx.model.dto.seckill;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeckillSkuCreateDto {

    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    @NotNull(message = "秒杀价格不能为空")
    @DecimalMin(value = "0.00", message = "秒杀价格不能小于0")
    @Digits(integer = 8, fraction = 2, message = "秒杀价格整数最多8位且小数最多2位")
    private BigDecimal seckillPrice;

    @NotNull(message = "活动库存不能为空")
    @Min(value = 1, message = "活动库存必须大于0")
    private Integer totalStock;
}

