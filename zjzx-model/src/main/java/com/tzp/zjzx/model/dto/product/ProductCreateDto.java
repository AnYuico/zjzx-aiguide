package com.tzp.zjzx.model.dto.product;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductCreateDto extends ProductWriteDto {

    @Valid
    private List<ProductSkuCreateDto> productSkuList;
}
