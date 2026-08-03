package com.tzp.zjzx.agent.mcp;

import com.tzp.zjzx.agent.client.ProductGuideCatalogClient;
import com.tzp.zjzx.agent.service.GuideSearchResponse;
import com.tzp.zjzx.agent.service.ShoppingGuideService;
import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCatalogMcpToolsTest {

    private ProductGuideCatalogClient productGuideCatalogClient;
    private ShoppingGuideService shoppingGuideService;
    private ProductCatalogMcpTools tools;

    @BeforeEach
    void setUp() {
        productGuideCatalogClient = mock(ProductGuideCatalogClient.class);
        shoppingGuideService = mock(ShoppingGuideService.class);
        tools = new ProductCatalogMcpTools(
                productGuideCatalogClient,
                shoppingGuideService
        );
    }

    @Test
    void searchesOnlyThroughPublicGuideContract() {
        ProductGuideVo product = product(14L);
        when(productGuideCatalogClient.search(any()))
                .thenReturn(Mono.just(List.of(product)));

        StepVerifier.create(tools.searchProducts("  Mac  ", 5))
                .expectNext(List.of(product))
                .verifyComplete();

        ArgumentCaptor<ProductGuideQueryDto> queryCaptor =
                ArgumentCaptor.forClass(ProductGuideQueryDto.class);
        verify(productGuideCatalogClient).search(queryCaptor.capture());
        assertEquals("Mac", queryCaptor.getValue().getKeyword());
        assertEquals(5, queryCaptor.getValue().getLimit());
    }

    @Test
    void getsRealtimeSkuSnapshot() {
        ProductGuideVo product = product(15L);
        when(productGuideCatalogClient.getBySkuId(15L))
                .thenReturn(Mono.just(product));

        StepVerifier.create(tools.getProductSnapshot(15L))
                .expectNext(product)
                .verifyComplete();
    }

    @Test
    void retrievesKnowledgeThroughRealtimeValidatedService() {
        ProductGuideVo product = product(16L);
        when(shoppingGuideService.search("small desktop computer", 3))
                .thenReturn(Mono.just(new GuideSearchResponse(
                        "small desktop computer",
                        "found",
                        1,
                        List.of(product)
                )));

        StepVerifier.create(tools.retrieveProductKnowledge(
                        "small desktop computer",
                        3
                ))
                .expectNext(List.of(product))
                .verifyComplete();
    }

    @Test
    void rejectsIdentityAndRangeShapedInvalidArguments() {
        StepVerifier.create(tools.getProductSnapshot(0L))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(tools.searchProducts("Mac", 21))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(tools.retrieveProductKnowledge(" ", 5))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    private ProductGuideVo product(Long skuId) {
        ProductGuideVo product = new ProductGuideVo();
        product.setSkuId(skuId);
        product.setProductName("Mac mini");
        product.setSkuName("16G");
        product.setSalePrice(new BigDecimal("1999.00"));
        product.setInStock(true);
        return product;
    }
}
