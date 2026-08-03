package com.tzp.zjzx.model.vo.product;

import lombok.Data;

@Data
public class ProductInfoVo {

    private Long id;
    private String name;
    private Long brandId;
    private Long category1Id;
    private Long category2Id;
    private Long category3Id;
    private String unitName;
    private Integer status;
}
