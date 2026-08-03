package com.tzp.zjzx.model.dto.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockItemDto {

    private Long skuId;

    private Integer skuNum;
}
