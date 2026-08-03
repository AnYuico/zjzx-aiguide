package com.tzp.zjzx.product.controller;

import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.dto.product.StockReserveRequest;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.product.service.InventoryService;
import com.tzp.zjzx.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerInternalContractTest {

    @Mock
    private ProductService productService;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private ProductController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "internalApiToken", "internal-token");
    }

    @Test
    void skuEndpointUsesUnifiedResultContract() {
        ProductSkuInternalDto sku = new ProductSkuInternalDto();
        when(productService.getInternalSku(1L)).thenReturn(sku);

        Result<ProductSkuInternalDto> result =
                controller.getBySkuId("internal-token", 1L);

        assertEquals(ResultCodeEnum.SUCCESS.getCode(), result.getCode());
        assertEquals(sku, result.getData());
    }

    @Test
    void reserveEndpointUsesUnifiedResultContract() {
        StockReserveRequest request = new StockReserveRequest();

        Result<Boolean> result = controller.reserveStock("internal-token", request);

        assertEquals(ResultCodeEnum.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData());
        verify(inventoryService).reserveStock(request);
    }
}
