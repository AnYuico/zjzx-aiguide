package com.tzp.zjzx.model.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public abstract class ProductSkuWriteDto {

    private String thumbImg;

    @DecimalMin(value = "0.00", message = "SKU售价不能小于0")
    @Digits(integer = 8, fraction = 2, message = "SKU售价整数最多8位且小数最多2位")
    private BigDecimal salePrice;

    @DecimalMin(value = "0.00", message = "SKU市场价不能小于0")
    @Digits(integer = 8, fraction = 2, message = "SKU市场价整数最多8位且小数最多2位")
    private BigDecimal marketPrice;

    @DecimalMin(value = "0.00", message = "SKU成本价不能小于0")
    @Digits(integer = 8, fraction = 2, message = "SKU成本价整数最多8位且小数最多2位")
    private BigDecimal costPrice;

    @Min(value = 0, message = "SKU库存不能小于0")
    private Integer stockNum;

    private String skuSpec;

    @DecimalMin(value = "0.00", message = "SKU重量不能小于0")
    @Digits(integer = 8, fraction = 2, message = "SKU重量整数最多8位且小数最多2位")
    private BigDecimal weight;

    @DecimalMin(value = "0.00", message = "SKU体积不能小于0")
    @Digits(integer = 8, fraction = 2, message = "SKU体积整数最多8位且小数最多2位")
    private BigDecimal volume;
}
