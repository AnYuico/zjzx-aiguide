package com.tzp.zjzx.product.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.dto.product.StockItemDto;
import com.tzp.zjzx.model.dto.product.StockReserveRequest;
import com.tzp.zjzx.model.entity.product.InventoryRequest;
import com.tzp.zjzx.model.enums.InventoryRequestStatus;
import com.tzp.zjzx.product.mapper.InventoryRequestMapper;
import com.tzp.zjzx.product.mapper.InventoryReservationMapper;
import com.tzp.zjzx.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryTransactionServiceTest {

    @Mock
    private ProductSkuMapper productSkuMapper;
    @Mock
    private InventoryRequestMapper inventoryRequestMapper;
    @Mock
    private InventoryReservationMapper inventoryReservationMapper;

    private InventoryTransactionService service;

    @BeforeEach
    void setUp() {
        service = new InventoryTransactionService(
                productSkuMapper, inventoryRequestMapper, inventoryReservationMapper);
    }

    @Test
    void reserveStockRejectsWholeRequestWhenAnySkuIsInsufficient() {
        StockReserveRequest request = request("order-1");
        when(inventoryRequestMapper.insertIgnore(any())).thenReturn(1);
        when(inventoryRequestMapper.selectByOrderNoForUpdate("order-1"))
                .thenReturn(inventoryRequest("order-1", "hash", InventoryRequestStatus.PROCESSING.getCode()));
        when(productSkuMapper.reserveStock(1L, 2)).thenReturn(1);
        when(productSkuMapper.reserveStock(2L, 3)).thenReturn(0);
        when(inventoryReservationMapper.insert(any())).thenReturn(1);

        assertThrows(MyException.class, () -> service.reserveStock(request, "hash"));

        verify(inventoryRequestMapper, never()).updateStatus(
                any(), anyInt(), anyInt());
    }

    @Test
    void reserveStockIsIdempotentForAnAlreadyReservedOrder() {
        StockReserveRequest request = request("order-2");
        when(inventoryRequestMapper.insertIgnore(any())).thenReturn(0);
        when(inventoryRequestMapper.selectByOrderNoForUpdate("order-2"))
                .thenReturn(inventoryRequest("order-2", "hash", InventoryRequestStatus.RESERVED.getCode()));

        service.reserveStock(request, "hash");

        verify(productSkuMapper, never()).reserveStock(anyLong(), anyInt());
        verify(inventoryReservationMapper, never()).insert(any());
    }

    @Test
    void confirmStockIsIdempotentAfterConfirmation() {
        when(inventoryRequestMapper.selectByOrderNoForUpdate("order-3"))
                .thenReturn(inventoryRequest("order-3", "hash", InventoryRequestStatus.CONFIRMED.getCode()));

        service.confirmStock("order-3");

        verify(inventoryReservationMapper, never()).selectByOrderNoForUpdate(any());
        verify(productSkuMapper, never()).increaseSale(anyLong(), anyInt());
    }

    private StockReserveRequest request(String orderNo) {
        StockReserveRequest request = new StockReserveRequest();
        request.setOrderNo(orderNo);
        request.setItems(List.of(new StockItemDto(1L, 2), new StockItemDto(2L, 3)));
        return request;
    }

    private InventoryRequest inventoryRequest(String orderNo, String hash, int status) {
        InventoryRequest request = new InventoryRequest();
        request.setOrderNo(orderNo);
        request.setItemsHash(hash);
        request.setStatus(status);
        return request;
    }
}
