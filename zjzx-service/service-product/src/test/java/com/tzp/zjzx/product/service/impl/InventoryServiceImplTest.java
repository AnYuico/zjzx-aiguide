package com.tzp.zjzx.product.service.impl;

import com.tzp.zjzx.model.dto.product.StockItemDto;
import com.tzp.zjzx.model.dto.product.StockReserveRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryTransactionService transactionService;

    @Test
    void reserveStockMergesDuplicatesAndSortsSkuIds() {
        InventoryServiceImpl service = new InventoryServiceImpl(transactionService);
        StockReserveRequest request = new StockReserveRequest();
        request.setOrderNo("order-1");
        request.setItems(List.of(
                new StockItemDto(5L, 1),
                new StockItemDto(2L, 3),
                new StockItemDto(5L, 4)));

        service.reserveStock(request);

        ArgumentCaptor<StockReserveRequest> captor = ArgumentCaptor.forClass(StockReserveRequest.class);
        verify(transactionService).reserveStock(captor.capture(), anyString());
        List<StockItemDto> items = captor.getValue().getItems();
        assertEquals(List.of(2L, 5L),
                items.stream().map(StockItemDto::getSkuId).collect(Collectors.toList()));
        assertEquals(List.of(3, 5),
                items.stream().map(StockItemDto::getSkuNum).collect(Collectors.toList()));
    }
}
