package com.tzp.zjzx.agent.controller;

import com.tzp.zjzx.agent.exception.ProductCatalogUnavailableException;
import com.tzp.zjzx.agent.service.GuideSearchResponse;
import com.tzp.zjzx.agent.service.ShoppingGuideService;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(ShoppingGuideController.class)
@Import(AgentExceptionHandler.class)
class ShoppingGuideControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ShoppingGuideService shoppingGuideService;

    @Test
    void exposesDeterministicGuideSearchApi() {
        ProductGuideVo product = new ProductGuideVo();
        product.setSkuId(14L);
        product.setProductName("Mac mini");
        when(shoppingGuideService.search(eq("Mac"), eq(5)))
                .thenReturn(Mono.just(new GuideSearchResponse(
                        "Mac",
                        "已找到 1 个相关商品。",
                        1,
                        List.of(product)
                )));

        webTestClient.post()
                .uri("/api/agent/guide/search")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "keyword": "Mac",
                          "limit": 5
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.count").isEqualTo(1)
                .jsonPath("$.products[0].skuId").isEqualTo(14)
                .jsonPath("$.products[0].costPrice").doesNotExist();
    }

    @Test
    void rejectsInvalidLimitAtHttpBoundary() {
        webTestClient.post()
                .uri("/api/agent/guide/search")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "keyword": "Mac",
                          "limit": 21
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void returnsControlledDegradationWhenCatalogIsUnavailable() {
        when(shoppingGuideService.search(eq("Mac"), eq(5)))
                .thenReturn(Mono.error(
                        new ProductCatalogUnavailableException("upstream unavailable")
                ));

        webTestClient.post()
                .uri("/api/agent/guide/search")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "keyword": "Mac",
                          "limit": 5
                        }
                        """)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PRODUCT_CATALOG_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("商品目录暂时不可用，请稍后重试。");
    }
}
