package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCatalogToolsTest {

    private final ShoppingGuideService shoppingGuideService =
            mock(ShoppingGuideService.class);

    @Test
    void capsModelSuppliedLimitAndCapturesAllowlistedProducts() {
        ProductGuideVo product = new ProductGuideVo();
        product.setSkuId(14L);
        product.setProductName("Mac mini");
        when(shoppingGuideService.search(eq("Mac"), any(Integer.class)))
                .thenReturn(Mono.just(new GuideSearchResponse(
                        "Mac",
                        "已找到 1 个相关商品。",
                        1,
                        List.of(product)
                )));
        ProductCatalogTools tools = new ProductCatalogTools(
                shoppingGuideService,
                5,
                Duration.ofSeconds(1)
        );

        List<ProductGuideVo> result = tools.searchProducts("Mac", 20);

        assertEquals(1, result.size());
        assertEquals(14L, tools.products().get(0).getSkuId());
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(shoppingGuideService).search(eq("Mac"), limitCaptor.capture());
        assertEquals(5, limitCaptor.getValue());
    }

    @Test
    void rejectsInvalidModelSuppliedLimit() {
        ProductCatalogTools tools = new ProductCatalogTools(
                shoppingGuideService,
                5,
                Duration.ofSeconds(1)
        );

        assertThrows(IllegalArgumentException.class,
                () -> tools.searchProducts("Mac", 0));
    }
}
