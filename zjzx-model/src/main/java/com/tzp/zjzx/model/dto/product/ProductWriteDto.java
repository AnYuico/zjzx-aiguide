package com.tzp.zjzx.model.dto.product;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public abstract class ProductWriteDto {

    @NotBlank(message = "商品名称不能为空")
    private String name;
    private Long brandId;
    private Long category1Id;
    private Long category2Id;
    private Long category3Id;
    private String unitName;
    private String sliderUrls;
    private String specValue;
    private String detailsImageUrls;
}
