package com.tzp.zjzx.model.dto.product;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductSkuUpdateDto extends ProductSkuWriteDto {

    @NotNull(message = "修改商品时SKU ID不能为空")
    private Long id;
}
