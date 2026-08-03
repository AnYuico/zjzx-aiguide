package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShoppingGuideServiceTest {

    private final HybridProductRetrievalService retrievalService =
            mock(HybridProductRetrievalService.class);
    private final ShoppingGuideService service =
            new ShoppingGuideService(retrievalService);

    @Test
    void normalizesInputAndBuildsDeterministicResponse() {
        ProductGuideVo product = new ProductGuideVo();
        product.setSkuId(14L);
        product.setProductName("Mac mini");
        when(retrievalService.search(eq("Mac"), anyInt()))
                .thenReturn(Mono.just(List.of(product)));

        StepVerifier.create(service.search("  Mac  ", null))
                .assertNext(response -> {
                    assertEquals("Mac", response.keyword());
                    assertEquals(1, response.count());
                    assertEquals("已找到 1 个相关商品。", response.message());
                    assertEquals(14L, response.products().get(0).getSkuId());
                })
                .verifyComplete();

        verify(retrievalService).search(
                "Mac",
                ShoppingGuideService.DEFAULT_LIMIT
        );
    }

    @Test
    void returnsExplicitEmptyResultWithoutInventingProducts() {
        when(retrievalService.search(isNull(), eq(5)))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.search(" ", 5))
                .assertNext(response -> {
                    assertNull(response.keyword());
                    assertEquals(0, response.count());
                    assertEquals("暂未找到符合条件的商品，请尝试更换关键词。", response.message());
                })
                .verifyComplete();
    }

    @Test
    void rejectsInvalidLimitBeforeCallingProductService() {
        StepVerifier.create(service.search("Mac", 21))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().contains("1 到 20"))
                .verify();
    }
}
