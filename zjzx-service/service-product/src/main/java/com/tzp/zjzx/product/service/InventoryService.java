package com.tzp.zjzx.product.service;

import com.tzp.zjzx.model.dto.product.StockReserveRequest;

public interface InventoryService {

    void reserveStock(StockReserveRequest request);

    void confirmStock(String orderNo);

    void releaseStock(String orderNo);
}
