package com.tzp.zjzx.model.dto.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductUpdateDto extends ProductWriteDto {

    @NotNull(message = "商品ID不能为空")
    private Long id;

    @Valid
    private List<ProductSkuUpdateDto> productSkuList;
}
