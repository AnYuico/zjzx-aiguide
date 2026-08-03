package com.tzp.zjzx.model.dto.product;

import lombok.Data;

import java.util.List;

@Data
public class StockReserveRequest {

    private String orderNo;

    private List<StockItemDto> items;
}
